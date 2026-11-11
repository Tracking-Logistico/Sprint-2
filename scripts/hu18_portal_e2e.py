import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import signal
import tempfile
import subprocess
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

import psycopg
from psycopg import sql
from psycopg.conninfo import conninfo_to_dict
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]
API = "http://127.0.0.1:18189"
UI = "http://127.0.0.1:5179"


def wait_http(url, process):
    for _ in range(180):
        if process.poll() is not None:
            raise RuntimeError("Un servicio de pruebas terminó. Revisa su registro temporal.")
        try:
            with urllib.request.urlopen(url, timeout=2):
                return
        except (OSError, urllib.error.URLError):
            time.sleep(0.5)
    raise RuntimeError("El servicio de pruebas no estuvo disponible a tiempo")


def request(path, token, method="GET", body=None, extra=None):
    headers = {"Authorization": "Bearer " + token}
    headers.update(extra or {})
    if body is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(API + "/api/v1" + path, headers=headers, method=method,
                                 data=None if body is None else json.dumps(body).encode())
    with urllib.request.urlopen(req, timeout=10) as response:
        data = response.read()
        return json.loads(data) if data else None


def context_for(browser, role, token, user):
    context = browser.new_context(viewport={"width": 390, "height": 844})
    session = {"state": {"accessToken": token, "refreshToken": None, "role": role,
                         "panel": role.lower(), "userId": user, "requiresPasswordChange": False,
                         "accessTokenExpiresAt": "2099-01-01T00:00:00Z"}, "version": 0}
    context.add_init_script("if (location.origin === " + json.dumps(UI) + ") localStorage.setItem('logistrack-session', " + json.dumps(json.dumps(session)) + ");")
    return context


def main():
    info = conninfo_to_dict(os.environ["HU18_E2E_POSTGRES_DSN"])
    if info.get("host") not in {"localhost", "127.0.0.1"}:
        raise RuntimeError("Este E2E requiere PostgreSQL local de pruebas")
    schema = "hu18_e2e_" + secrets.token_hex(8)
    connection = psycopg.connect(os.environ["HU18_E2E_POSTGRES_DSN"], autocommit=True)
    processes = []
    logs = []
    try:
        connection.execute(sql.SQL("CREATE SCHEMA {}").format(sql.Identifier(schema)))
        connection.execute(sql.SQL("SET search_path TO {}").format(sql.Identifier(schema)))
        migrations = sorted((ROOT / "tracking-identidad-api/core/src/main/resources/db/migration").glob("V*.sql"),
                            key=lambda p: int(p.name.split("__")[0][1:]))
        for migration in migrations:
            connection.execute(migration.read_text())
        now = datetime.now(timezone.utc).replace(tzinfo=None)

        def user(role):
            uid = connection.execute("""INSERT INTO usuarios(nombre,email,password,rol,estado,acepto_terminos)
                VALUES (%s,%s,%s,%s,'ACTIVO',false) RETURNING id""",
                ("Cuenta sintética", secrets.token_hex(8) + "@example.invalid", "sin-credencial-utilizable", role)).fetchone()[0]
            token = secrets.token_urlsafe(32)
            digest = lambda value: base64.urlsafe_b64encode(hashlib.sha256(value.encode()).digest()).decode().rstrip("=")
            connection.execute("""INSERT INTO sesiones_usuario(usuario_id,access_token_hash,refresh_token_hash,
                access_token_expires_at,refresh_token_expires_at,last_activity_at) VALUES (%s,%s,%s,%s,%s,%s)""",
                (uid, digest(token), digest(secrets.token_urlsafe(32)), now+timedelta(hours=2), now+timedelta(days=1), now))
            return uid, token

        operator, operator_token = user("OPERADOR")
        connection.execute("INSERT INTO operadores(id_usuario,codigo_empleado) VALUES (%s,%s)", (operator, secrets.token_hex(6)))
        owner, owner_token = user("CLIENTE")
        client = connection.execute("INSERT INTO clientes(id_usuario,ciudad) VALUES (%s,%s) RETURNING id_cliente", (owner, "Ciudad sintética")).fetchone()[0]
        stranger, stranger_token = user("CLIENTE")
        connection.execute("INSERT INTO clientes(id_usuario,ciudad) VALUES (%s,%s)", (stranger, "Ciudad sintética"))
        city = connection.execute("INSERT INTO ciudades_logisticas(nombre,departamento) VALUES (%s,%s) RETURNING id", ("Ciudad sintética", "Pruebas")).fetchone()[0]
        office = connection.execute("""INSERT INTO oficinas_logisticas(nombre,ciudad_id,direccion,telefono)
            VALUES (%s,%s,%s,%s) RETURNING id""", ("Oficina sintética",city,"Ubicación sintética","+9990000001")).fetchone()[0]
        fallback_office = connection.execute("""INSERT INTO oficinas_logisticas(nombre,ciudad_id,direccion,telefono)
            VALUES (%s,%s,%s,%s) RETURNING id""", ("Oficina <img src=x onerror=alert(1)>",city,"Otra ubicación sintética","")).fetchone()[0]

        def shipment(state, location=None, hours=0, office_id=office):
            tracking = "T-HU18-" + secrets.token_hex(6)
            pid = connection.execute("""INSERT INTO pedidos(numero_pedido,numero_tracking,id_cliente,direccion_origen,
                direccion_destino,descripcion_paquete,peso_kg,largo_cm,ancho_cm,alto_cm,tipo_servicio,prioridad_sugerida,
                estado,fecha_creacion) VALUES (%s,%s,%s,%s,%s,%s,1,1,1,1,'ESTANDAR','MEDIA',%s,%s) RETURNING id_pedido""",
                ("P-"+tracking,tracking,client,"Origen sintético","Destino sintético","Paquete de prueba",state,now-timedelta(days=2))).fetchone()[0]
            if location:
                connection.execute("""INSERT INTO eventos_ubicacion_pedido(id_evento,id_pedido,numero_tracking,etapa,
                    estado_pedido,ciudad_id,ciudad_nombre,oficina_id,oficina_nombre,usuario_id,origen,fecha_evento)
                    VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,'SELECTOR',%s)""",
                    (secrets.token_hex(16),pid,tracking,location,state,city,"Ciudad sintética",office_id,
                     "Oficina sintética",operator,now-timedelta(hours=hours)))
            return pid, tracking

        permanent, tracking = shipment("RECIBIDO_EN_ORIGEN","EN_BODEGA_ORIGEN",25)
        arrival, arrival_tracking = shipment("EN_TRANSITO","LLEGADO_OFICINA_DESTINO",3,fallback_office)
        retries, retries_tracking = shipment("EN_REPARTO")
        dba = secrets.token_urlsafe(32)
        env = os.environ.copy()
        env.update({"DB_JDBC_URL": f"jdbc:postgresql://127.0.0.1:{info.get('port','5432')}/{info.get('dbname','postgres')}?currentSchema={schema}",
                    "DB_USER": info.get("user", ""), "DB_PASSWORD": info.get("password", ""), "SPRING_PROFILE":"postgres",
                    "FLYWAY_ENABLED":"false", "RABBITMQ_ENABLED":"false", "MAIL_ENABLED":"false",
                    "DBA_API_KEY":dba, "ANOMALIAS_CONTACTO_GENERAL":"+9990000000", "CORS_ALLOWED_ORIGIN":UI,
                    "PORT":"18189", "OPERATIONS_TIME_ZONE":"UTC", "VITE_API_ORIGIN":API})
        java = os.environ.get("HU18_JAVA_BIN", "java")
        jar = ROOT / "tracking-envios-api/api/target/tracking-envios-api-0.0.1-SNAPSHOT.jar"
        log_dir = Path(os.environ.get("HU18_E2E_LOG_DIR", tempfile.gettempdir()))
        log_dir.mkdir(parents=True,exist_ok=True)
        logs.append((log_dir/"hu18-e2e-backend.log").open("w"))
        backend = subprocess.Popen([java,"-Duser.timezone=UTC","-jar",str(jar),"--app.api.role=pedidos",
                                   "--app.anomalias.inicio-ms=0","--app.anomalias.intervalo-ms=1000"],env=env,stdout=logs[-1],stderr=subprocess.STDOUT,start_new_session=os.name=="posix")
        processes.append(backend)
        wait_http(API+"/internal/health",backend)
        logs.append((log_dir/"hu18-e2e-frontend.log").open("w"))
        frontend = subprocess.Popen(["npm","run","dev","--","--host","127.0.0.1","--port","5179","--strictPort"],
                                    cwd=ROOT/"tracking-logistico-frontend",env=env,stdout=logs[-1],stderr=subprocess.STDOUT,start_new_session=os.name=="posix")
        processes.append(frontend)
        wait_http(UI,frontend)
        for _ in range(30):
            if request("/pedidos/mios/tracking/"+tracking+"/alertas",owner_token):break
            time.sleep(.2)
        else:raise AssertionError("El job no generó la alerta")
        alerts = request("/pedidos/mios/tracking/"+tracking+"/alertas",owner_token)
        assert set(alerts[0]) == {"mensaje","oficina","telefono","enlaceLlamada"}
        try:
            request("/pedidos/mios/tracking/"+tracking+"/alertas",stranger_token)
            raise AssertionError("Un cliente ajeno pudo consultar el envío")
        except urllib.error.HTTPError as error:
            assert error.code == 403

        request(f"/pedidos/{retries}/incidencias",operator_token,"POST",{"tipo":"CLIENTE_AUSENTE"})
        request("/pedidos/mios/tracking/"+retries_tracking+"/reprogramacion",owner_token,"POST",{"fecha":now.date().isoformat()})
        request(f"/pedidos/{retries}/incidencias",operator_token,"POST",{"tipo":"CLIENTE_AUSENTE"})
        assert connection.execute("SELECT count(*) FROM anomalias WHERE envio_id=%s AND tipo='REINTENTOS_ACUMULADOS'",(retries,)).fetchone()[0] == 1
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(headless=True, executable_path=os.environ.get("HU18_CHROMIUM_BIN"),
                                                 args=json.loads(os.environ.get("HU18_CHROMIUM_ARGS", "[]")))
            customer = context_for(browser,"CLIENTE",owner_token,owner)
            customer_page = customer.new_page()
            customer_page.goto(UI+"/panel/pedidos/seguimiento/"+tracking)
            call = customer_page.get_by_role("link",name="Llamar a la oficina responsable")
            expect(call).to_have_attribute("href","tel:+9990000001")
            operator_browser = playwright.chromium.launch(headless=True, executable_path=os.environ.get("HU18_CHROMIUM_BIN"),
                                                          args=json.loads(os.environ.get("HU18_CHROMIUM_ARGS", "[]")))
            operator_context = context_for(operator_browser,"OPERADOR",operator_token,operator)
            operator_page = operator_context.new_page()
            operator_page.on("dialog",lambda dialog:dialog.accept())
            operator_page.goto(UI+"/panel/alertas")
            card = operator_page.locator("article").filter(has_text=tracking)
            expect(card).to_be_visible()
            card.get_by_role("button",name="Ver detalle").click()
            expect(operator_page.get_by_label("Detalle de alerta")).to_be_visible()
            card.get_by_role("button",name="Resolver",exact=True).click()
            expect(card).to_have_count(0)
            with customer_page.expect_response(lambda response: response.url.endswith("/alertas")) as refreshed:
                customer_page.reload()
            assert refreshed.value.status == 200 and refreshed.value.json() == []
            expect(customer_page.get_by_text("Consulta el estado actual",exact=False)).to_be_visible()
            expect(customer_page.get_by_role("link",name="Llamar a la oficina responsable")).to_have_count(0)

            customer_page.goto(UI+"/panel/pedidos/seguimiento/"+arrival_tracking)
            expect(customer_page.get_by_role("link",name="Llamar a la oficina responsable")).to_have_attribute("href","tel:+9990000000")
            expect(customer_page.locator('[aria-live="polite"] img')).to_have_count(0)
            receipt = operator_page.get_by_label("Recepciones pendientes").locator("div").filter(has_text=arrival_tracking).last
            receipt.get_by_role("button",name="Confirmar recepción en oficina").click()
            with customer_page.expect_response(lambda response: response.url.endswith("/alertas")) as refreshed:
                customer_page.reload()
            assert refreshed.value.status == 200 and refreshed.value.json() == []
            expect(customer_page.get_by_text("Consulta el estado actual",exact=False)).to_be_visible()
            expect(customer_page.get_by_role("link",name="Llamar a la oficina responsable")).to_have_count(0)
            operator_page.get_by_label("Clave DBA",exact=True).fill(dba)
            operator_page.get_by_role("button",name="Consultar parámetros").click()
            operator_page.get_by_label("Permanencia X",exact=True).fill("12")
            operator_page.get_by_role("button",name="Guardar umbrales").click()
            expect(operator_page.get_by_text("Parámetros guardados.",exact=False)).to_be_visible()
            expect(operator_page.get_by_label("Clave DBA",exact=True)).to_have_value("")
            assert operator_page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")
            assert connection.execute("SELECT usuario_id FROM historial_parametros_anomalia ORDER BY id DESC LIMIT 1").fetchone()[0] == operator
            operator_browser.close()
            browser.close()
        print("HU-18 E2E OK — backend real, job, PostgreSQL, portal móvil, ciclo de vida, llamada, fallback, anti-IDOR, texto escapado y parámetros auditados")
    finally:
        for process in reversed(processes):
            try:
                if os.name == "posix": os.killpg(process.pid, signal.SIGTERM)
                else: process.terminate()
            except ProcessLookupError:
                pass
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                if os.name == "posix": os.killpg(process.pid, signal.SIGKILL)
                else: process.kill()
                process.wait()
        for log in logs: log.close()
        connection.execute(sql.SQL("DROP SCHEMA IF EXISTS {} CASCADE").format(sql.Identifier(schema)))
        connection.close()


if __name__ == "__main__":
    main()

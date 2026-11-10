#!/usr/bin/env bash
# HU-13 / 13-DB-03: prueba de punta a punta de que un usuario con perfil incompleto NO queda bloqueado.
#
# Arranca la API de identidad real (perfil postgres, Flyway hasta la última migración) contra un PostgreSQL
# temporal. Crea usuarios sintéticos "anteriores a HU-13" (sin cédula ni fecha de nacimiento) y comprueba:
#   - V26 los marca como incompletos y solo exige completar al CLIENTE;
#   - el cliente y el operador inician sesión y usan su sesión (consultar y editar perfil);
#   - la autorización sigue intacta: rol incorrecto -> 403, sin sesión -> 401, token alterado -> 401;
#   - no se revoca ninguna sesión y la contraseña no aparece en los logs.
# La contraseña y la clave de cifrado se generan al azar en cada ejecución; no hay secretos en el script.
#
# Uso: scripts/probar-login-perfil-incompleto.sh   (antes: sh ./mvnw -pl api -am package -DskipTests en identidad)
# Requiere: initdb, pg_ctl, psql, createdb, curl, openssl, python3 y java.
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_JAR=$(ls "$RAIZ"/tracking-identidad-api/api/target/tracking-identidad-api-*.jar 2>/dev/null | head -1)
[[ -n "$APP_JAR" ]] || { echo "Compila primero la API de identidad (mvnw package)" >&2; exit 2; }
PUERTO_DB=${PUERTO_DB:-55432}
PUERTO_API=${PUERTO_API:-58081}

TRABAJO="$(mktemp -d "${TMPDIR:-/tmp}/login-incompleto.XXXXXX")"; chmod 700 "$TRABAJO"
SOCKET="$(mktemp -d /tmp/pgsock.XXXXXX)"; chmod 700 "$SOCKET"
APP_PID=""
limpiar() {
    [[ -n "$APP_PID" ]] && kill "$APP_PID" 2>/dev/null || true
    pg_ctl -D "$TRABAJO/datos" -m immediate stop >/dev/null 2>&1 || true
    rm -rf "$TRABAJO" "$SOCKET"
    return 0
}
trap limpiar EXIT

export PSQLRC=/dev/null
initdb -D "$TRABAJO/datos" -U postgres --auth=trust --no-sync -E UTF8 --locale=C.UTF-8 >/dev/null
pg_ctl -D "$TRABAJO/datos" -w -l "$TRABAJO/pg.log" \
    -o "-c listen_addresses=127.0.0.1 -p $PUERTO_DB -c unix_socket_directories='$SOCKET' -c fsync=off" start >/dev/null
createdb -h 127.0.0.1 -p "$PUERTO_DB" -U postgres tracking_psep
Q() { psql -X -q -v ON_ERROR_STOP=1 -h 127.0.0.1 -p "$PUERTO_DB" -U postgres -d tracking_psep -At -c "$1"; }

echo "==> Arrancando la API de identidad (Flyway completo)"
SPRING_PROFILE=postgres DB_HOST=127.0.0.1 DB_PORT="$PUERTO_DB" DB_NAME=tracking_psep DB_USER=postgres DB_PASSWORD=no-usada \
FLYWAY_ENABLED=true RABBITMQ_ENABLED=false MAIL_ENABLED=false PORT="$PUERTO_API" \
IDENTIDAD_CLAVE_MAESTRA="$(openssl rand -base64 32)" java -jar "$APP_JAR" > "$TRABAJO/app.log" 2>&1 &
APP_PID=$!
for _ in $(seq 1 90); do sleep 1; curl -sf "localhost:$PUERTO_API/internal/health" >/dev/null && break; done
curl -sf "localhost:$PUERTO_API/internal/health" >/dev/null || { echo "La API no arrancó" >&2; tail -30 "$TRABAJO/app.log" >&2; exit 1; }

echo "==> Creando usuarios sintéticos sin datos de identidad (como los anteriores a HU-13)"
PASSWORD=$(openssl rand -base64 18)
cat > "$TRABAJO/Hash.java" <<'EOF'
public class Hash { public static void main(String[] a) { System.out.print(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(a[0])); } }
EOF
# BCrypt con la misma librería que empaqueta la aplicación (sin depender de ~/.m2).
python3 -I - "$APP_JAR" "$TRABAJO/lib" <<'EOF'
import sys, zipfile, os
os.makedirs(sys.argv[2], exist_ok=True)
with zipfile.ZipFile(sys.argv[1]) as z:
    for n in z.namelist():
        base = os.path.basename(n)
        if n.startswith('BOOT-INF/lib/') and base.startswith(('spring-security-crypto-', 'spring-core-', 'spring-jcl-', 'commons-logging-')):
            with open(os.path.join(sys.argv[2], base), 'wb') as f:
                f.write(z.read(n))
EOF
HASH=$(java -cp "$TRABAJO/lib/*" "$TRABAJO/Hash.java" "$PASSWORD")
for rol in CLIENTE OPERADOR; do
    Q "INSERT INTO usuarios (nombre, email, password, rol, estado, acepto_terminos, fecha_aceptacion_terminos)
       VALUES ('Sintetico $rol', lower('$rol') || '@e2e.invalid', '$HASH', '$rol', 'ACTIVO', TRUE, now())"
done
Q "INSERT INTO clientes (id_usuario) SELECT id FROM usuarios WHERE email = 'cliente@e2e.invalid'"
Q "INSERT INTO operadores (id_usuario, codigo_empleado) SELECT id, 'EMP-SINT-1' FROM usuarios WHERE email = 'operador@e2e.invalid'"

FALLAS=0
comprobar() { # descripción esperado obtenido
    if [[ "$2" == "$3" ]]; then echo "    OK    $1"; else echo "    FALLA $1 (esperado: $2, obtenido: $3)"; FALLAS=$((FALLAS + 1)); fi
}
campo() { python3 -c "import sys,json; print(json.load(sys.stdin).get('$1', ''))"; }
login() { curl -s -X POST "localhost:$PUERTO_API/api/v1/auth/login" -H 'Content-Type: application/json' \
              -d "{\"email\":\"$1\",\"password\":\"$PASSWORD\"}"; }
codigo() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

echo "==> Comprobaciones"
comprobar "V26 marca al cliente y le exige completar" "CLIENTE:t" \
    "$(Q "SELECT v.rol || ':' || CASE WHEN v.debe_completar_en_login THEN 't' ELSE 'f' END FROM v_usuarios_perfil_incompleto v JOIN usuarios u ON u.id = v.id_usuario WHERE u.email = 'cliente@e2e.invalid'")"
comprobar "V26 marca al operador sin exigirle completar" "OPERADOR:f" \
    "$(Q "SELECT v.rol || ':' || CASE WHEN v.debe_completar_en_login THEN 't' ELSE 'f' END FROM v_usuarios_perfil_incompleto v JOIN usuarios u ON u.id = v.id_usuario WHERE u.email = 'operador@e2e.invalid'")"

TOKEN=$(login cliente@e2e.invalid | campo accessToken)
comprobar "El cliente incompleto inicia sesión" "si" "$([[ -n "$TOKEN" ]] && echo si || echo no)"
comprobar "Consulta su perfil (GET /clientes/me)" "200" \
    "$(codigo "localhost:$PUERTO_API/api/v1/clientes/me" -H "Authorization: Bearer $TOKEN")"
comprobar "Edita su perfil (PUT /clientes/me/perfil)" "200" \
    "$(codigo -X PUT "localhost:$PUERTO_API/api/v1/clientes/me/perfil" -H "Authorization: Bearer $TOKEN" \
        -H 'Content-Type: application/json' -d '{"nombre":"Sintetico Editado","telefono":"3000000000","direccion":"Calle sintetica 1"}')"
comprobar "El operador incompleto inicia sesión" "si" \
    "$([[ -n "$(login operador@e2e.invalid | campo accessToken)" ]] && echo si || echo no)"
comprobar "Negativo: rol incorrecto (cliente -> /admin/usuarios)" "403" \
    "$(codigo "localhost:$PUERTO_API/api/v1/admin/usuarios" -H "Authorization: Bearer $TOKEN")"
comprobar "Negativo: sin sesión" "401" "$(codigo "localhost:$PUERTO_API/api/v1/clientes/me")"
comprobar "Negativo: token alterado" "401" \
    "$(codigo "localhost:$PUERTO_API/api/v1/clientes/me" -H "Authorization: Bearer ${TOKEN}x")"
comprobar "Negativo: contraseña incorrecta" "401" \
    "$(codigo -X POST "localhost:$PUERTO_API/api/v1/auth/login" -H 'Content-Type: application/json' \
        -d '{"email":"cliente@e2e.invalid","password":"incorrecta"}')"
comprobar "Ninguna sesión revocada por la marca" "0" "$(Q "SELECT count(*) FROM sesiones_usuario WHERE revoked_at IS NOT NULL")"
comprobar "La marca sigue (no se inventaron datos al iniciar sesión)" "t" \
    "$(Q "SELECT CASE WHEN bool_and(perfil_incompleto) THEN 't' ELSE 'f' END FROM usuarios WHERE email LIKE '%@e2e.invalid'")"
comprobar "La contraseña no aparece en los logs" "0" "$(grep -cF "$PASSWORD" "$TRABAJO/app.log" || true)"

(( FALLAS == 0 )) || { echo "==> $FALLAS comprobación(es) fallida(s)" >&2; exit 1; }
echo "==> OK: el perfil incompleto queda marcado sin bloquear la sesión"

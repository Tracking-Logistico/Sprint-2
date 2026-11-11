import asyncio
import json
import os
import unittest
from playwright.async_api import async_playwright, expect


class AssistantBrowserTest(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.runtime = await async_playwright().start()
        options = {"headless": True}
        if os.getenv("HU12_BROWSER_EXECUTABLE"):
            options.update(executable_path=os.environ["HU12_BROWSER_EXECUTABLE"], args=["--no-zygote", "--single-process"])
        self.browser = await self.runtime.chromium.launch(**options)
        self.context = await self.browser.new_context(viewport={"width": 390, "height": 844})
        self.page = await self.context.new_page()

    async def asyncTearDown(self):
        await self.browser.close()
        await self.runtime.stop()

    async def open_role(self, role):
        session = {"state": {"accessToken": "sesion-sintetica", "refreshToken": "sin-uso", "role": role,
                    "userId": 1, "requiresPasswordChange": False, "isHydrated": True}, "version": 0}
        await self.page.add_init_script("localStorage.setItem('logistrack-session', " + json.dumps(json.dumps(session)) + ")")
        await self.page.route("**/api/v1/**", lambda route: route.fulfill(status=200, json={"nombre": "Cuenta sintética"}))
        await self.page.goto(os.getenv("HU12_FRONT_URL", "http://127.0.0.1:5173") + "/panel/configuracion")

    async def test_cliente_sin_asistente(self):
        await self.open_role("CLIENTE")
        await expect(self.page.get_by_role("button", name="Asistente interno")).to_have_count(0)

    async def test_conductor_sin_asistente(self):
        await self.open_role("CONDUCTOR")
        await expect(self.page.get_by_role("button", name="Asistente interno")).to_have_count(0)

    async def test_operador_carga_escape_guardrail_y_limpieza(self):
        await self.open_role("OPERADOR")
        count = 0
        async def answer(route):
            nonlocal count
            count += 1
            body = route.request.post_data_json
            assert body["origen"] == "WEB" and body["solicitudId"]
            assert route.request.headers["authorization"] == "Bearer sesion-sintetica"
            await asyncio.sleep(0.4)
            messages = ["<img src=x onerror=alert(1)> ****7890", "Esa información está fuera de mi alcance autorizado. Consulta el estado de un paquete.", "No encontré resultados."]
            await route.fulfill(status=200, json={"sessionId": "12345678-1234-1234-1234-123456789012", "resultado": ["RESUELTA", "FUERA_DE_ALCANCE", "SIN_DATOS"][count-1], "mensaje": messages[count-1]})
        await self.page.route("**/assistant/query", answer)
        await self.page.get_by_role("button", name="Asistente interno").click()
        for query in ["Perfil de Persona Sintética", "Métricas de desempeño", "Dónde está el paquete NCA-INEXISTENTE"]:
            await self.page.get_by_label("Tu consulta").fill(query)
            await self.page.get_by_role("button", name="Consultar", exact=True).click()
            await expect(self.page.get_by_role("status")).to_contain_text("Generando respuesta")
            await expect(self.page.get_by_label("Tu consulta")).to_be_enabled()
        await expect(self.page.get_by_text("No encontré resultados.", exact=True)).to_be_visible()
        await expect(self.page.locator('[aria-label="Conversación"] img')).to_have_count(0)
        await expect(self.page.get_by_text("<img src=x onerror=alert(1)> ****7890", exact=True)).to_be_visible()
        assert await self.page.locator("body").evaluate("e => e.scrollWidth <= window.innerWidth")
        await self.page.get_by_role("button", name="Cerrar asistente").click()
        await self.page.get_by_role("button", name="Asistente interno").click()
        await expect(self.page.get_by_text("No encontré resultados.", exact=True)).to_have_count(0)

    async def test_timeout_mensaje_generico(self):
        await self.open_role("OPERADOR")
        async def delayed(route):
            await asyncio.sleep(11)
            try:
                await route.fulfill(status=200, json={})
            except Exception:
                pass
        await self.page.route("**/assistant/query", delayed)
        await self.page.get_by_role("button", name="Asistente interno").click()
        await self.page.get_by_label("Tu consulta").fill("Perfil de Persona Sintética")
        await self.page.get_by_role("button", name="Consultar", exact=True).click()
        await expect(self.page.get_by_role("alert")).to_contain_text("tardó más de lo esperado", timeout=11500)


if __name__ == "__main__":
    unittest.main(verbosity=2)

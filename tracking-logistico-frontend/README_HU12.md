# HU-12 · Asistente interno

El widget se integra en AppShell del repositorio independiente del frontend. Utiliza el endpoint POST /assistant/query del Orquestador a través de VITE_API_URL. La URL debe incluir /api/v1 y HTTPS en despliegue. No configurar la clave Gemini en este repositorio.

Solo se renderiza para OPERADOR y se elimina al salir de su panel. El backend conserva la decisión de autorización. El chat muestra el estado de carga, limita cada consulta a 500 caracteres, cancela a los 10 segundos y presenta texto escapado. Cerrar el chat borra preguntas, respuestas y sesión de la memoria. No se guarda el chat en almacenamiento persistente.

Cada consulta debe incluir el nombre/correo o tracking. No se implementa contexto conversacional entre preguntas. El backend debe estar actualizado con HU-12 y configurado con Gemini y un lector PostgreSQL de permisos mínimos.

## Validación

```powershell
npm ci
npm run build
npm run lint
```

Para pruebas de navegador con respuestas HTTP simuladas, en una terminal:

```powershell
npm run dev
```

En otra terminal, desde este repositorio:

```powershell
python -m pip install -r .\tests\requirements-assistant.txt
python -m playwright install chromium
python .\tests\assistant_browser.py
```

Las cuatro pruebas cubren Cliente y Conductor sin widget, Operador con carga/escape de HTML/guardrail/sin datos/limpieza y pantalla de 390 px, además del timeout. No usan credenciales reales y no llaman a Gemini. HU12_FRONT_URL permite cambiar la URL local del frontend.

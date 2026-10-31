# API de Rutas — Tracking logístico

Este proyecto contiene código fuente completo, Maven Wrapper, migraciones y configuración. Para la **puesta en marcha del conjunto** usa `docker-compose.yml` de `tracking-orquestador-api` situado al lado de estas tres carpetas. El frontend solo debe hablar con `http://localhost:8080/api/v1`.

Este proyecto conserva su `docker-compose.yml` individual para desarrollo, pero **no lo levantes simultáneamente con la pila principal**, ya que comparte infraestructura y nombre de proyecto Docker. Al usar el orquestador, las APIs individuales no publican puertos al host.

- Identidad: puerto interno 8081.
- Envíos: puerto interno 8082 e incluye HU-14.
- Rutas: puerto interno 8083.
- Las mismas variables `DB_*`, `RABBITMQ_*`, `MAIL_*`, `AUTH_*` siguen siendo compatibles.
- La API de identidad es la única que aplica migraciones Flyway, previa a las otras.

Las APIs siguen autorizando cada acción desde su `SecurityConfig`, mientras el orquestador conserva rutas HTTP y cabeceras sin cambiar la lógica....

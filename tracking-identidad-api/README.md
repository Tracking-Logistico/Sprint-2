# API de Identidad — Tracking logístico

Este proyecto contiene código fuente completo, Maven Wrapper, migraciones y configuración. Para la **puesta en marcha del conjunto** usa `docker-compose.yml` de `tracking-orquestador-api` situado al lado de estas tres carpetas. El frontend solo debe hablar con `http://localhost:8080/api/v1`.

Este proyecto conserva su `docker-compose.yml` individual para desarrollo, pero **no lo levantes simultáneamente con la pila principal**, ya que comparte infraestructura y nombre de proyecto Docker. Al usar el orquestador, las APIs individuales no publican puertos al host.

- Identidad: puerto interno 8081.
- Envíos: puerto interno 8082 e incluye HU-14.
- Rutas: puerto interno 8083.
- Las mismas variables `DB_*`, `RABBITMQ_*`, `MAIL_*`, `AUTH_*` siguen siendo compatibles.
- La API de identidad es la única que aplica migraciones Flyway, previa a las otras.

Las APIs siguen autorizando cada acción desde su `SecurityConfig`, mientras el orquestador conserva rutas HTTP y cabeceras sin cambiar la lógica.

## HU-13: datos de identidad cifrados (migración V24)

- `usuarios` guarda la cédula y la fecha de nacimiento **solo cifradas** con AES-256-GCM. La unicidad de la cédula se controla con un índice ciego HMAC-SHA256 (`numero_cedula_hash`).
- `datos_identidad_bloqueados` hace los datos inmutables. Un trigger de PostgreSQL rechaza cualquier cambio, venga o no de la API.
- **`IDENTIDAD_CLAVE_MAESTRA` es obligatoria** (al menos 32 caracteres, `openssl rand -base64 32`). Sin ella la API no arranca. Si se pierde o se cambia, las cédulas ya guardadas no se pueden leer, así que conviene guardarla en un gestor de secretos y con respaldo. Solo la usa esta API.
- Reversión manual: `db/rollback/V24__rollback_identidad_cedula_fecha_nacimiento.sql`. Lee las advertencias del archivo antes de usarla.

## HU-13: verificación de edad en envíos (migración V25)

- `pedidos.requiere_verificacion_edad`: lo marca el remitente. Solo se puede cambiar antes de la validación del operador (SOLICITADO sin `fecha_validacion`, o CORRECCION_SOLICITADA); lo impone un trigger.
- `verificacion_edad_log`: un único resultado por envío (PK `id_pedido`), append-only. Solo guarda envío, conductor, resultado (`CUMPLE`/`NO_CUMPLE`) y fecha UTC; nunca el documento ni la fecha de nacimiento.
- La BD solo acepta el log del **conductor de la parada PENDIENTE** de un envío 18+ en reparto, y no deja pasar a `ENTREGADO` un envío 18+ sin un `CUMPLE`. Por eso el backend debe **insertar el log (saveAndFlush) antes** de cerrar la parada o cambiar el estado del envío.
- Reversión manual: `db/rollback/V25__rollback_verificacion_edad_envios.sql`.

## HU-13: usuarios existentes con perfil incompleto (migración V26)

- `usuarios.perfil_incompleto` es una **columna generada**: vale TRUE cuando faltan la cédula o la fecha de nacimiento. No inventa datos, nadie puede escribirla y se apaga sola cuando el usuario completa sus datos.
- La migración no cambia `estado`, `activo` ni las sesiones: ningún usuario pierde el acceso.
- `v_usuarios_perfil_incompleto` (solo lectura, sin datos personales) indica a quién se le exige completar en el siguiente login: **solo a CLIENTE**. Los internos quedan marcados y los completa la administración. La exigencia en el login se implementa en una tarea posterior, junto con el endpoint para completar.
- Al revertir, el orden es primero V26 y después V24.
- Prueba de punta a punta (login de un usuario incompleto sin bloqueo): `scripts/probar-login-perfil-incompleto.sh`.

## Probar migraciones sobre una copia

`scripts/probar-migracion.sh <versión> [--dump respaldo.dump]`, por ejemplo `scripts/probar-migracion.sh 25`. Hace la subida, ejecuta `db/pruebas/VN__verificar.sql`, aplica el rollback dos veces, comprueba que esquema y filas vuelven al estado previo y repite la subida. Si existe `db/pruebas/VN__semilla.sql`, siembra con él los datos sintéticos previos.

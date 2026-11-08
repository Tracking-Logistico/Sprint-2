-- Datos sintéticos previos a V26 (dominio .invalid, sin datos reales). Los usa V26__verificar.sql.
-- Cubre usuarios de los tres roles en distintos estados, uno con identidad completa (con un cifrado y un hash
-- sintéticos en el formato de V24) y sesiones activas, para comprobar que la migración no las toca.
INSERT INTO usuarios (nombre, email, password, rol, estado, activo, acepto_terminos, fecha_aceptacion_terminos) VALUES
    ('Cliente Activo', 'cliente-activo@v26.invalid', 'no-es-un-hash', 'CLIENTE', 'ACTIVO', TRUE, TRUE, now()),
    ('Cliente Sin Verificar', 'cliente-pendiente@v26.invalid', 'no-es-un-hash', 'CLIENTE', 'PENDIENTE_VERIFICACION', TRUE, TRUE, now()),
    ('Cliente Inactivo', 'cliente-inactivo@v26.invalid', 'no-es-un-hash', 'CLIENTE', 'INACTIVO', FALSE, TRUE, now()),
    ('Operador Sintetico', 'operador@v26.invalid', 'no-es-un-hash', 'OPERADOR', 'ACTIVO', TRUE, TRUE, now()),
    ('Conductor Temporal', 'conductor@v26.invalid', 'no-es-un-hash', 'CONDUCTOR', 'PENDIENTE_ACTIVACION', TRUE, TRUE, now()),
    ('Cliente Completo', 'cliente-completo@v26.invalid', 'no-es-un-hash', 'CLIENTE', 'ACTIVO', TRUE, TRUE, now());

UPDATE usuarios
   SET numero_cedula_cifrado = 'v1:' || translate(encode(sha256('sintetico-c'::bytea), 'base64'), '+/=', '-_'),
       numero_cedula_hash = encode(sha256('sintetico-h'::bytea), 'hex'),
       fecha_nacimiento_cifrada = 'v1:' || translate(encode(sha256('sintetico-f'::bytea), 'base64'), '+/=', '-_'),
       datos_identidad_bloqueados = TRUE,
       fecha_bloqueo_identidad = (now() AT TIME ZONE 'UTC')
 WHERE email = 'cliente-completo@v26.invalid';

-- Sesiones vigentes (hashes de relleno, no son tokens reales).
INSERT INTO sesiones_usuario (usuario_id, access_token_hash, refresh_token_hash, access_token_expires_at,
                              refresh_token_expires_at, last_activity_at)
SELECT id, md5('acc' || id), md5('ref' || id), now() + interval '15 minutes', now() + interval '7 days', now()
  FROM usuarios WHERE email LIKE '%@v26.invalid';

-- Foto del estado previo para comparar después de la migración (solo existe en la copia de prueba).
-- Se guarda la fila completa en JSON para comparar TODAS las columnas, no solo algunas.
CREATE TABLE _v26_foto_usuarios AS SELECT id, to_jsonb(u) AS fila FROM usuarios u;
CREATE TABLE _v26_foto_sesiones AS SELECT id, to_jsonb(s) AS fila FROM sesiones_usuario s;

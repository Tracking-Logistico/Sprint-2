-- Usuarios sintéticos previos a V24 (dominio .invalid, sin datos reales). Los usa V24__verificar.sql.
INSERT INTO usuarios (nombre, email, password, rol, estado, acepto_terminos, fecha_aceptacion_terminos)
VALUES ('Usuario Sintetico A', 'legado-a@v24.invalid', 'no-es-un-hash', 'CLIENTE', 'ACTIVO', TRUE, now()),
       ('Usuario Sintetico B', 'legado-b@v24.invalid', 'no-es-un-hash', 'OPERADOR', 'ACTIVO', TRUE, now()),
       ('Usuario Sintetico C', 'legado-c@v24.invalid', 'no-es-un-hash', 'CONDUCTOR', 'ACTIVO', TRUE, now());

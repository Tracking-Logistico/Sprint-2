-- Datos sintéticos previos a V27 (dominio .invalid, sin datos reales). Los usa V27__verificar.sql.
-- Actores: un cliente remitente, dos conductores, un operador con fila en conductores (dato inconsistente
-- adrede, para probar que el rol se valida) y un envío anterior a la migración.
INSERT INTO usuarios (nombre, email, password, rol, estado, acepto_terminos, fecha_aceptacion_terminos) VALUES
    ('Cliente Sintetico', 'cliente@v27.invalid', 'no-es-un-hash', 'CLIENTE', 'ACTIVO', TRUE, now()),
    ('Conductor Uno', 'conductor-1@v27.invalid', 'no-es-un-hash', 'CONDUCTOR', 'ACTIVO', TRUE, now()),
    ('Conductor Dos', 'conductor-2@v27.invalid', 'no-es-un-hash', 'CONDUCTOR', 'ACTIVO', TRUE, now()),
    ('Operador Sintetico', 'operador@v27.invalid', 'no-es-un-hash', 'OPERADOR', 'ACTIVO', TRUE, now());

INSERT INTO clientes (id_usuario) SELECT id FROM usuarios WHERE email = 'cliente@v27.invalid';
INSERT INTO conductores (id_usuario, licencia)
SELECT id, 'LIC-SINTETICA-' || id FROM usuarios
 WHERE email IN ('conductor-1@v27.invalid', 'conductor-2@v27.invalid', 'operador@v27.invalid');

INSERT INTO rutas (id_conductor, fecha)
SELECT c.id_conductor, DATE '2026-11-07' FROM conductores c JOIN usuarios u ON u.id = c.id_usuario
 WHERE u.email LIKE '%@v27.invalid';

INSERT INTO pedidos (numero_pedido, id_cliente, direccion_origen, direccion_destino, descripcion_paquete,
                     peso_kg, largo_cm, ancho_cm, alto_cm, tipo_servicio, prioridad_sugerida, estado)
SELECT 'V27-LEGADO', cl.id_cliente, 'Origen sintetico 1', 'Destino sintetico 1', 'Paquete sintetico',
       1, 10, 10, 10, 'ESTANDAR', 'MEDIA', 'EN_REPARTO'
  FROM clientes cl JOIN usuarios u ON u.id = cl.id_usuario WHERE u.email = 'cliente@v27.invalid';

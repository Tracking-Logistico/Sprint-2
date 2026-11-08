-- HU-13 / 13-DB-02 (CA-4, CA-6): verificación de edad 18+ en envíos.
--
-- Va en la API de identidad porque es la única que ejecuta Flyway sobre la BD compartida, igual que V22 y V23.
--
-- * pedidos.requiere_verificacion_edad: lo marca el Cliente Remitente al crear el envío (CA-4). Solo se puede
--   cambiar mientras el envío está en SOLICITADO o CORRECCION_SOLICITADA. Después de la validación del
--   operador (aunque el envío siga en SOLICITADO con fecha_validacion) queda fijo, para que nadie lo
--   quite durante el reparto. Lo garantiza un trigger.
-- * verificacion_edad_log: un único resultado por envío, append-only. Por requisito de minimización
--   (Ley 1581 de 2012) guarda SOLO cuatro campos: envío, conductor, resultado (CUMPLE/NO_CUMPLE) y fecha (UTC).
--   No hay columna para el número de documento, la fecha de nacimiento, imágenes ni texto libre.
--
-- Reversión: tracking-identidad-api/db/rollback/V25__rollback_verificacion_edad_envios.sql
-- Pruebas sobre una copia: scripts/probar-migracion.sh 25

ALTER TABLE pedidos ADD COLUMN requiere_verificacion_edad BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN pedidos.requiere_verificacion_edad IS
    'HU-13: verificación de edad 18+ exigida por el remitente. Solo editable antes de la validación del operador';

-- CA-4: el flag solo cambia mientras el remitente puede editar el envío, antes y después del UPDATE:
--   * SOLICITADO sin fecha_validacion (al aprobar, Pedido.validar deja SOLICITADO pero registra la fecha), o
--   * CORRECCION_SOLICITADA (el remitente corrige; corregir() vuelve a SOLICITADO con fecha_validacion NULL).
-- Un envío nuevo con el flag activo debe nacer en SOLICITADO sin validar. Así ni una devolución ni un alta
-- interna pueden crear un envío 18+ ya validado sin pasar por el remitente.
CREATE OR REPLACE FUNCTION fn_pedidos_verificacion_edad_es_editable(estado VARCHAR, fecha_validacion TIMESTAMP)
RETURNS BOOLEAN AS $$
    SELECT (estado = 'SOLICITADO' AND fecha_validacion IS NULL) OR estado = 'CORRECCION_SOLICITADA'
$$ LANGUAGE sql IMMUTABLE;

CREATE OR REPLACE FUNCTION fn_pedidos_verificacion_edad_editable() RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.requiere_verificacion_edad
           AND NOT (NEW.estado = 'SOLICITADO' AND NEW.fecha_validacion IS NULL) THEN
            RAISE EXCEPTION 'La verificación de edad solo se define al solicitar el envío'
                USING ERRCODE = 'check_violation';
        END IF;
    ELSIF NEW.requiere_verificacion_edad IS DISTINCT FROM OLD.requiere_verificacion_edad
          AND NOT (fn_pedidos_verificacion_edad_es_editable(OLD.estado, OLD.fecha_validacion)
                   AND fn_pedidos_verificacion_edad_es_editable(NEW.estado, NEW.fecha_validacion)) THEN
        RAISE EXCEPTION 'La verificación de edad ya no se puede modificar en este estado del envío'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_pedidos_verificacion_edad_editable
    BEFORE INSERT OR UPDATE ON pedidos
    FOR EACH ROW
    EXECUTE FUNCTION fn_pedidos_verificacion_edad_editable();

-- La PK sobre id_pedido impone un resultado por envío y hace idempotente el registro: un reintento o
-- una carrera entre dos peticiones produce unique_violation, y la aplicación responde con el registro existente.
CREATE TABLE verificacion_edad_log (
    id_pedido BIGINT PRIMARY KEY REFERENCES pedidos(id_pedido) ON DELETE RESTRICT,
    id_usuario_conductor BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    resultado VARCHAR(10) NOT NULL,
    fecha_verificacion TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_verificacion_edad_resultado CHECK (resultado IN ('CUMPLE', 'NO_CUMPLE'))
);
COMMENT ON TABLE verificacion_edad_log IS
    'HU-13: solo el resultado de la verificación de edad. Nunca documento, fecha de nacimiento ni imágenes';
CREATE INDEX idx_verificacion_edad_conductor ON verificacion_edad_log(id_usuario_conductor, fecha_verificacion);

-- Antes de insertar, la BD exige (defensa en profundidad y anti-IDOR):
--   1. que el envío tenga activa la verificación 18+ y esté en reparto;
--   2. que el usuario sea el CONDUCTOR de la ruta con la parada PENDIENTE de ese envío. Un conductor ajeno,
--      el de una parada ya reasignada o cualquier otro rol queda rechazado.
-- Por eso la aplicación debe insertar el log ANTES de cerrar la parada o cambiar el estado del envío,
-- por ejemplo con saveAndFlush.
CREATE OR REPLACE FUNCTION fn_verificacion_edad_validar() RETURNS TRIGGER AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pedidos p
         WHERE p.id_pedido = NEW.id_pedido
           AND p.requiere_verificacion_edad
           AND p.estado IN ('EN_REPARTO', 'ENTREGA_REPROGRAMADA')
    ) THEN
        RAISE EXCEPTION 'El envío no admite verificación de edad en su estado actual'
            USING ERRCODE = 'check_violation';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM paradas_ruta pr
          JOIN rutas r ON r.id_ruta = pr.id_ruta
          JOIN conductores c ON c.id_conductor = r.id_conductor
          JOIN usuarios u ON u.id = c.id_usuario
         WHERE pr.id_pedido = NEW.id_pedido
           AND pr.estado = 'PENDIENTE'
           AND u.id = NEW.id_usuario_conductor
           AND u.rol = 'CONDUCTOR'
    ) THEN
        RAISE EXCEPTION 'La verificación de edad solo la registra el conductor asignado al envío'
            USING ERRCODE = 'insufficient_privilege';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_verificacion_edad_validar
    BEFORE INSERT ON verificacion_edad_log
    FOR EACH ROW
    EXECUTE FUNCTION fn_verificacion_edad_validar();

-- CA-6 (y CA-5 y CA-7, como defensa en profundidad): un envío 18+ solo pasa a ENTREGADO si existe un
-- resultado CUMPLE. Cubre todos los caminos que hoy llegan a ENTREGADO: el resultado de entrega en rutas
-- y el checkpoint ENTREGADO en envíos, además de cualquier UPDATE manual.
CREATE OR REPLACE FUNCTION fn_pedidos_entrega_requiere_verificacion() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.estado = 'ENTREGADO' AND OLD.estado IS DISTINCT FROM 'ENTREGADO' AND NEW.requiere_verificacion_edad
       AND NOT EXISTS (SELECT 1 FROM verificacion_edad_log l
                        WHERE l.id_pedido = NEW.id_pedido AND l.resultado = 'CUMPLE') THEN
        RAISE EXCEPTION 'El envío requiere verificación de edad aprobada antes de marcarse como entregado'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_pedidos_entrega_requiere_verificacion
    BEFORE UPDATE OF estado ON pedidos
    FOR EACH ROW
    EXECUTE FUNCTION fn_pedidos_entrega_requiere_verificacion();

-- Append-only: el resultado registrado no se corrige ni se borra (mismo criterio que historial_pedidos, HU-14).
CREATE OR REPLACE FUNCTION fn_verificacion_edad_inmutable() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'El resultado de la verificación de edad es inmutable'
        USING ERRCODE = 'check_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_verificacion_edad_inmutable
    BEFORE UPDATE OR DELETE ON verificacion_edad_log
    FOR EACH ROW
    EXECUTE FUNCTION fn_verificacion_edad_inmutable();

-- TRUNCATE no dispara los triggers de fila, así que se bloquea con un trigger de sentencia.
CREATE TRIGGER trg_verificacion_edad_sin_truncate
    BEFORE TRUNCATE ON verificacion_edad_log
    FOR EACH STATEMENT
    EXECUTE FUNCTION fn_verificacion_edad_inmutable();

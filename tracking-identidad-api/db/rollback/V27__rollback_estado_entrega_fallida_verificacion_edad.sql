-- Reversión manual de V27__estado_entrega_fallida_verificacion_edad.sql (HU-13 CA-7).
--
-- Vive fuera de classpath:db/migration porque Flyway Community no tiene "undo".
-- Si ya hay envíos en ENTREGA_FALLIDA_VERIFICACION_EDAD, reducir la columna o restaurar el CHECK anterior FALLA
-- a propósito y la transacción se revierte entera. No se inventa un estado de reemplazo: hay que decidir
-- antes qué hacer con esos envíos.
--
--   psql -v ON_ERROR_STOP=1 --single-transaction -f V27__rollback_estado_entrega_fallida_verificacion_edad.sql
-- Es idempotente.

DROP TRIGGER IF EXISTS trg_pedidos_fallo_verificacion_requiere_log ON pedidos;
DROP FUNCTION IF EXISTS fn_pedidos_fallo_verificacion_requiere_log();

-- Orden: primero el tipo de la columna y después el CHECK, para que el CHECK quede idéntico al de V22.
-- El trigger de V25 depende del tipo de la columna: se quita y se vuelve a crear igual.
ALTER TABLE pedidos DROP CONSTRAINT IF EXISTS chk_pedidos_estado;
DROP TRIGGER IF EXISTS trg_pedidos_entrega_requiere_verificacion ON pedidos;
ALTER TABLE pedidos ALTER COLUMN estado TYPE VARCHAR(30);
CREATE TRIGGER trg_pedidos_entrega_requiere_verificacion
    BEFORE UPDATE OF estado ON pedidos
    FOR EACH ROW
    EXECUTE FUNCTION fn_pedidos_entrega_requiere_verificacion();
ALTER TABLE pedidos ADD CONSTRAINT chk_pedidos_estado CHECK (estado IN (
    'SOLICITADO','CORRECCION_SOLICITADA','CREADO','RECIBIDO_EN_ORIGEN','EN_TRANSITO','EN_REPARTO','ENTREGADO',
    'RECHAZADO','ENTREGA_FALLIDA','ENTREGA_REPROGRAMADA','DIRECCION_POR_VERIFICAR','DEVOLUCION_AL_REMITENTE',
    'ENTREGA_FALLIDA_CERRADA','PAUSADO','CANCELADO'
));

DO $$
BEGIN
    IF to_regclass('flyway_schema_history') IS NOT NULL THEN
        DELETE FROM flyway_schema_history WHERE version = '27';
    END IF;
END;
$$;

-- HU-13 CA-7: estado final "Entrega fallida (Verificación de edad no aprobada)".
--
-- * Agrega ENTREGA_FALLIDA_VERIFICACION_EDAD a la lista blanca de estados de pedidos.
-- * Defensa en profundidad: un envío solo puede entrar en ese estado si existe en verificacion_edad_log un
--   resultado NO_CUMPLE para él (V25). Ningún otro flujo ni ningún UPDATE manual puede marcarlo así.
--   Por eso la aplicación inserta el log antes de cambiar el estado (saveAndFlush).
-- * El retorno a bodega lo crea la aplicación con el flujo de devoluciones existente (DevolucionIniciadaEvent).
--
-- Reversión: tracking-identidad-api/db/rollback/V27__rollback_estado_entrega_fallida_verificacion_edad.sql
-- Pruebas sobre una copia: scripts/probar-migracion.sh 27

-- El nombre del estado tiene 33 caracteres y la columna era VARCHAR(30). Se amplía a 40, como
-- estado_antes_pausa (V22). En PostgreSQL ampliar un VARCHAR solo cambia metadatos: no reescribe la tabla.
-- El trigger de V25 se define "UPDATE OF estado" y PostgreSQL no deja cambiar el tipo de la columna con él
-- presente. Se quita y se vuelve a crear idéntico dentro de esta misma transacción.
-- Orden: se quita el CHECK, se cambia el tipo y se vuelve a crear el CHECK sobre el tipo nuevo.
ALTER TABLE pedidos DROP CONSTRAINT IF EXISTS chk_pedidos_estado;
DROP TRIGGER trg_pedidos_entrega_requiere_verificacion ON pedidos;
ALTER TABLE pedidos ALTER COLUMN estado TYPE VARCHAR(40);
CREATE TRIGGER trg_pedidos_entrega_requiere_verificacion
    BEFORE UPDATE OF estado ON pedidos
    FOR EACH ROW
    EXECUTE FUNCTION fn_pedidos_entrega_requiere_verificacion();

ALTER TABLE pedidos ADD CONSTRAINT chk_pedidos_estado CHECK (estado IN (
    'SOLICITADO','CORRECCION_SOLICITADA','CREADO','RECIBIDO_EN_ORIGEN','EN_TRANSITO','EN_REPARTO','ENTREGADO',
    'RECHAZADO','ENTREGA_FALLIDA','ENTREGA_REPROGRAMADA','DIRECCION_POR_VERIFICAR','DEVOLUCION_AL_REMITENTE',
    'ENTREGA_FALLIDA_CERRADA','PAUSADO','CANCELADO','ENTREGA_FALLIDA_VERIFICACION_EDAD'
));

CREATE OR REPLACE FUNCTION fn_pedidos_fallo_verificacion_requiere_log() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.estado = 'ENTREGA_FALLIDA_VERIFICACION_EDAD'
       AND (TG_OP = 'INSERT' OR OLD.estado IS DISTINCT FROM 'ENTREGA_FALLIDA_VERIFICACION_EDAD')
       AND NOT EXISTS (SELECT 1 FROM verificacion_edad_log l
                        WHERE l.id_pedido = NEW.id_pedido AND l.resultado = 'NO_CUMPLE') THEN
        RAISE EXCEPTION 'El estado de verificación de edad no aprobada requiere un resultado NO_CUMPLE registrado'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_pedidos_fallo_verificacion_requiere_log
    BEFORE INSERT OR UPDATE OF estado ON pedidos
    FOR EACH ROW
    EXECUTE FUNCTION fn_pedidos_fallo_verificacion_requiere_log();

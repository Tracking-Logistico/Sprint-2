-- Reversión manual de V25__verificacion_edad_envios.sql (HU-13 / 13-DB-02).
--
-- Vive fuera de classpath:db/migration porque Flyway Community no tiene "undo".
--
-- ADVERTENCIA: borra de forma irreversible los resultados de verificación de edad y el flag 18+ de los
-- envíos. Si hay envíos 18+ en reparto, perderán la restricción. Antes de ejecutarlo:
--   1. Detén las tres APIs (identidad, envíos y rutas).
--   2. Saca un respaldo: pg_dump --format=custom ... > respaldo_pre_rollback_v25.dump
--   3. Ejecútalo en una sola transacción:
--        psql -v ON_ERROR_STOP=1 --single-transaction -f V25__rollback_verificacion_edad_envios.sql
--   4. Despliega una versión del código sin los campos de 13-DB-02. Con el código actual,
--      ddl-auto=validate falla porque no encuentra las columnas.
--
-- Es idempotente: se puede ejecutar más de una vez sin error.

-- DROP TABLE elimina también sus triggers e índices.
DROP TABLE IF EXISTS verificacion_edad_log;
DROP FUNCTION IF EXISTS fn_verificacion_edad_inmutable();
DROP FUNCTION IF EXISTS fn_verificacion_edad_validar();

DROP TRIGGER IF EXISTS trg_pedidos_entrega_requiere_verificacion ON pedidos;
DROP FUNCTION IF EXISTS fn_pedidos_entrega_requiere_verificacion();
DROP TRIGGER IF EXISTS trg_pedidos_verificacion_edad_editable ON pedidos;
DROP FUNCTION IF EXISTS fn_pedidos_verificacion_edad_es_editable(VARCHAR, TIMESTAMP);
DROP FUNCTION IF EXISTS fn_pedidos_verificacion_edad_editable();
ALTER TABLE pedidos DROP COLUMN IF EXISTS requiere_verificacion_edad;

DO $$
BEGIN
    IF to_regclass('flyway_schema_history') IS NOT NULL THEN
        DELETE FROM flyway_schema_history WHERE version = '25';
    END IF;
END;
$$;

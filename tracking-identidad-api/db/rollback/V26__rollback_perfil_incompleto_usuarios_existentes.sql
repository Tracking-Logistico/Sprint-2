-- Reversión manual de V26__perfil_incompleto_usuarios_existentes.sql (HU-13 / 13-DB-03).
--
-- Vive fuera de classpath:db/migration porque Flyway Community no tiene "undo".
-- No borra datos de usuarios: la marca es una columna generada y se puede recalcular volviendo a aplicar V26.
--
-- Orden: si también se revierte V24, ejecuta ANTES este rollback. La columna generada depende de
-- numero_cedula_hash y fecha_nacimiento_cifrada, así que el rollback de V24 falla (de forma segura)
-- mientras V26 esté aplicada.
--
-- Ejecútalo en una sola transacción:
--   psql -v ON_ERROR_STOP=1 --single-transaction -f V26__rollback_perfil_incompleto_usuarios_existentes.sql
-- Es idempotente: se puede ejecutar más de una vez sin error.

-- DROP VIEW elimina también su trigger INSTEAD OF.
DROP VIEW IF EXISTS v_usuarios_perfil_incompleto;
DROP FUNCTION IF EXISTS fn_v_usuarios_perfil_incompleto_solo_lectura();
DROP INDEX IF EXISTS idx_usuarios_clientes_perfil_incompleto;
ALTER TABLE usuarios DROP COLUMN IF EXISTS perfil_incompleto;

DO $$
BEGIN
    IF to_regclass('flyway_schema_history') IS NOT NULL THEN
        DELETE FROM flyway_schema_history WHERE version = '26';
    END IF;
END;
$$;

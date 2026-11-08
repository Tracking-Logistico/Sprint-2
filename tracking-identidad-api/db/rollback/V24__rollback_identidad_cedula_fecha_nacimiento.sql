-- Reversión manual de V24__identidad_cedula_fecha_nacimiento.sql (HU-13 / 13-DB-01).
--
-- Flyway Community no tiene "undo", por eso este archivo vive FUERA de classpath:db/migration
-- y no se ejecuta automáticamente.
--
-- ADVERTENCIA: borra de forma irreversible la cédula y la fecha de nacimiento cifradas de todos
-- los usuarios. Antes de ejecutarlo:
--   1. Detén la API de identidad, para que no vuelva a aplicar V24 al arrancar.
--   2. Saca un respaldo: pg_dump --format=custom ... > respaldo_pre_rollback_v24.dump
--   3. Ejecútalo en una sola transacción:
--        psql -v ON_ERROR_STOP=1 --single-transaction -f V24__rollback_identidad_cedula_fecha_nacimiento.sql
--   4. Despliega una versión del código sin los campos de HU-13. Con el código actual,
--      ddl-auto=validate falla porque no encuentra las columnas.
--
-- Es idempotente: se puede ejecutar más de una vez sin error.

DROP TRIGGER IF EXISTS trg_usuarios_identidad_inmutable ON usuarios;
DROP FUNCTION IF EXISTS fn_usuarios_identidad_inmutable();

ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS uk_usuarios_numero_cedula_hash;
ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS chk_usuarios_cedula_hash_formato;
ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS chk_usuarios_fecha_nacimiento_cifrada_formato;
ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS chk_usuarios_cedula_cifrada_formato;
ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS chk_usuarios_identidad_bloqueo;
ALTER TABLE usuarios DROP CONSTRAINT IF EXISTS chk_usuarios_identidad_completa;

ALTER TABLE usuarios DROP COLUMN IF EXISTS fecha_bloqueo_identidad;
ALTER TABLE usuarios DROP COLUMN IF EXISTS datos_identidad_bloqueados;
ALTER TABLE usuarios DROP COLUMN IF EXISTS fecha_nacimiento_cifrada;
ALTER TABLE usuarios DROP COLUMN IF EXISTS numero_cedula_hash;
ALTER TABLE usuarios DROP COLUMN IF EXISTS numero_cedula_cifrado;

-- Quita V24 del historial para que Flyway la vuelva a aplicar en el próximo despliegue con HU-13.
-- La tabla puede no existir si el esquema se creó sin Flyway.
DO $$
BEGIN
    IF to_regclass('flyway_schema_history') IS NOT NULL THEN
        DELETE FROM flyway_schema_history WHERE version = '24';
    END IF;
END;
$$;

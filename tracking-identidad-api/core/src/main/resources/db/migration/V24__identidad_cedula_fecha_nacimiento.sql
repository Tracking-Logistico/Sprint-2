-- HU-13 / 13-DB-01 (CA-1, CA-3, CA-8): datos de identidad del usuario.
--
-- * numero_cedula_cifrado y fecha_nacimiento_cifrada guardan SOLO texto cifrado con AES-256-GCM, en el
--   formato "v<versión>:<base64url(iv || cifrado || tag)>". La clave vive fuera del código y de la BD
--   (variable de entorno IDENTIDAD_CLAVE_MAESTRA), así que con un volcado de la BD no basta para leerlos.
-- * numero_cedula_hash es un índice ciego: HMAC-SHA256 en hex de la cédula normalizada, calculado con
--   una subclave distinta. Es determinista, así que permite la unicidad sin guardar la cédula en claro.
-- * datos_identidad_bloqueados es el flag de inmutabilidad (CA-3). Se activa al registrar los datos, y
--   desde ese momento un trigger rechaza cualquier modificación, incluso si llega por fuera de la aplicación.
-- * Los usuarios existentes quedan sin datos de identidad (NULL). La obligatoriedad de CA-1 se valida en
--   la aplicación cuando se registra o crea un usuario. Nunca se guardan imágenes del documento (CA-8).
--
-- Reversión: tracking-identidad-api/db/rollback/V24__rollback_identidad_cedula_fecha_nacimiento.sql
-- Pruebas sobre una copia: scripts/probar-migracion.sh 24

ALTER TABLE usuarios ADD COLUMN numero_cedula_cifrado VARCHAR(255);
ALTER TABLE usuarios ADD COLUMN numero_cedula_hash VARCHAR(64);
ALTER TABLE usuarios ADD COLUMN fecha_nacimiento_cifrada VARCHAR(255);
ALTER TABLE usuarios ADD COLUMN datos_identidad_bloqueados BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE usuarios ADD COLUMN fecha_bloqueo_identidad TIMESTAMP;

COMMENT ON COLUMN usuarios.numero_cedula_cifrado IS 'AES-256-GCM. Nunca en claro. Ley 1581 de 2012';
COMMENT ON COLUMN usuarios.numero_cedula_hash IS 'Índice ciego HMAC-SHA256 (hex) para unicidad';
COMMENT ON COLUMN usuarios.fecha_nacimiento_cifrada IS 'AES-256-GCM. La edad se calcula en el servidor';
COMMENT ON COLUMN usuarios.datos_identidad_bloqueados IS 'TRUE: cédula y fecha de nacimiento inmutables';
COMMENT ON COLUMN usuarios.fecha_bloqueo_identidad IS 'Momento (UTC) en que se registraron y bloquearon';

-- Los tres datos se guardan juntos o no se guarda ninguno.
ALTER TABLE usuarios ADD CONSTRAINT chk_usuarios_identidad_completa CHECK (
    (numero_cedula_cifrado IS NULL AND numero_cedula_hash IS NULL AND fecha_nacimiento_cifrada IS NULL)
    OR (numero_cedula_cifrado IS NOT NULL AND numero_cedula_hash IS NOT NULL AND fecha_nacimiento_cifrada IS NOT NULL)
);

-- Datos registrados <=> bloqueados con su fecha de bloqueo.
ALTER TABLE usuarios ADD CONSTRAINT chk_usuarios_identidad_bloqueo CHECK (
    (datos_identidad_bloqueados = FALSE AND numero_cedula_hash IS NULL AND fecha_bloqueo_identidad IS NULL)
    OR (datos_identidad_bloqueados = TRUE AND numero_cedula_hash IS NOT NULL AND fecha_bloqueo_identidad IS NOT NULL)
);

-- Defensa en profundidad: la BD rechaza una cédula o fecha en claro que llegue por error.
ALTER TABLE usuarios ADD CONSTRAINT chk_usuarios_cedula_cifrada_formato
    CHECK (numero_cedula_cifrado IS NULL OR numero_cedula_cifrado ~ '^v[0-9]{1,3}:[A-Za-z0-9_-]{40,}$');
ALTER TABLE usuarios ADD CONSTRAINT chk_usuarios_fecha_nacimiento_cifrada_formato
    CHECK (fecha_nacimiento_cifrada IS NULL OR fecha_nacimiento_cifrada ~ '^v[0-9]{1,3}:[A-Za-z0-9_-]{40,}$');
ALTER TABLE usuarios ADD CONSTRAINT chk_usuarios_cedula_hash_formato
    CHECK (numero_cedula_hash IS NULL OR numero_cedula_hash ~ '^[0-9a-f]{64}$');

-- Unicidad de la cédula. PostgreSQL admite varios NULL, que son los usuarios anteriores a HU-13.
-- La constraint también protege frente a registros concurrentes de la misma cédula.
ALTER TABLE usuarios ADD CONSTRAINT uk_usuarios_numero_cedula_hash UNIQUE (numero_cedula_hash);

-- CA-3: una vez bloqueados, los datos no cambian, sin importar si el UPDATE viene de la API, de un
-- script o de una consola. La única excepción es la rotación de claves (mismo dato, recifrado), que
-- exige activar en la misma transacción: SET LOCAL app.rotacion_claves_identidad = 'on'.
-- Ni siquiera la rotación permite desbloquear o cambiar la fecha de bloqueo.
CREATE OR REPLACE FUNCTION fn_usuarios_identidad_inmutable() RETURNS TRIGGER AS $$
BEGIN
    IF OLD.datos_identidad_bloqueados THEN
        IF NEW.datos_identidad_bloqueados IS DISTINCT FROM OLD.datos_identidad_bloqueados
           OR NEW.fecha_bloqueo_identidad IS DISTINCT FROM OLD.fecha_bloqueo_identidad THEN
            RAISE EXCEPTION 'Los datos de identidad bloqueados no se pueden desbloquear'
                USING ERRCODE = 'check_violation';
        END IF;
        IF (NEW.numero_cedula_cifrado IS DISTINCT FROM OLD.numero_cedula_cifrado
            OR NEW.numero_cedula_hash IS DISTINCT FROM OLD.numero_cedula_hash
            OR NEW.fecha_nacimiento_cifrada IS DISTINCT FROM OLD.fecha_nacimiento_cifrada)
           AND COALESCE(current_setting('app.rotacion_claves_identidad', true), 'off') <> 'on' THEN
            RAISE EXCEPTION 'Los datos de identidad bloqueados son inmutables'
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_usuarios_identidad_inmutable
    BEFORE UPDATE ON usuarios
    FOR EACH ROW
    EXECUTE FUNCTION fn_usuarios_identidad_inmutable();

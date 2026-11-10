-- HU-13 / 13-DB-03 (CA-1): marca como "perfil incompleto" a los usuarios que no tienen cédula ni fecha de nacimiento.
--
-- Requisitos de seguridad:
-- * No inventa datos. No hay ningún UPDATE ni ningún valor por defecto para la cédula o la fecha de
--   nacimiento. La marca es una columna GENERADA que PostgreSQL calcula a partir de la ausencia de esos datos:
--     - nadie la puede escribir ni falsear (ni la aplicación ni un script);
--     - se apaga sola cuando el usuario registra sus datos (V24, Usuario.registrarDatosIdentidad);
--     - también marca a los usuarios que se creen sin identidad después de esta migración.
-- * No bloquea la sesión. No modifica estado, activo, sesiones_usuario ni intentos_inicio_sesion. Tampoco
--   dispara el trigger de inmutabilidad de V24 ni incrementa versiones. Ningún usuario pierde el acceso
--   por esta migración, y el flujo de completar queda disponible.
-- * Completar en el siguiente login solo se exige a CLIENTE. Los usuarios internos (OPERADOR, CONDUCTOR)
--   quedan marcados pero los completa la administración (HU-01B). La regla queda en la vista
--   v_usuarios_perfil_incompleto, que la tarea de login consumirá. Esta migración no cambia la autorización.
-- * Minimización (Ley 1581): la vista solo expone id, rol y si debe completar; no expone correo, nombre ni teléfono.
--
-- Nota de operación: ADD COLUMN ... STORED reescribe la tabla usuarios bajo un bloqueo exclusivo. Con el
-- volumen actual dura milisegundos, pero conviene desplegarla en una ventana de bajo tráfico.
--
-- Reversión: tracking-identidad-api/db/rollback/V26__rollback_perfil_incompleto_usuarios_existentes.sql
-- Pruebas sobre una copia: scripts/probar-migracion.sh 26

ALTER TABLE usuarios ADD COLUMN perfil_incompleto BOOLEAN NOT NULL
    GENERATED ALWAYS AS (numero_cedula_hash IS NULL OR fecha_nacimiento_cifrada IS NULL) STORED;

COMMENT ON COLUMN usuarios.perfil_incompleto IS
    'HU-13: TRUE si faltan la cédula o la fecha de nacimiento. Columna generada: no se escribe, se calcula';

-- Búsqueda directa de los clientes que deben completar al iniciar sesión.
CREATE INDEX idx_usuarios_clientes_perfil_incompleto ON usuarios (id)
    WHERE perfil_incompleto AND rol = 'CLIENTE';

CREATE VIEW v_usuarios_perfil_incompleto AS
SELECT u.id AS id_usuario,
       u.rol,
       (u.rol = 'CLIENTE') AS debe_completar_en_login
  FROM usuarios u
 WHERE u.perfil_incompleto;

COMMENT ON VIEW v_usuarios_perfil_incompleto IS
    'HU-13: usuarios sin cédula o fecha de nacimiento. Solo CLIENTE debe completarlas en el siguiente login. Solo lectura';

-- PostgreSQL hace "auto-actualizable" una vista simple sobre una sola tabla. Sin este trigger, un
-- UPDATE v_usuarios_perfil_incompleto SET rol = ... modificaría usuarios.rol (escalada de privilegios).
-- El trigger INSTEAD OF la deja estrictamente de solo lectura.
CREATE OR REPLACE FUNCTION fn_v_usuarios_perfil_incompleto_solo_lectura() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'v_usuarios_perfil_incompleto es de solo lectura'
        USING ERRCODE = 'feature_not_supported';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_v_usuarios_perfil_incompleto_solo_lectura
    INSTEAD OF INSERT OR UPDATE OR DELETE ON v_usuarios_perfil_incompleto
    FOR EACH ROW
    EXECUTE FUNCTION fn_v_usuarios_perfil_incompleto_solo_lectura();

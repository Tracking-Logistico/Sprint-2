-- Pruebas de V26 (HU-13 / 13-DB-03). Las ejecuta scripts/probar-migracion.sh 26 sobre una COPIA de la BD.
-- Todo corre en una transacción que termina en ROLLBACK: las pruebas no dejan datos. Todos los datos son sintéticos.
\set ON_ERROR_STOP on
\o /dev/null
BEGIN;

CREATE TEMP TABLE _v26_resultado (prueba TEXT PRIMARY KEY, ok BOOLEAN NOT NULL);

CREATE FUNCTION pg_temp.debe_fallar(nombre TEXT, sentencia TEXT, sqlstate_esperado TEXT) RETURNS VOID AS $$
BEGIN
    BEGIN
        EXECUTE sentencia;
        INSERT INTO _v26_resultado VALUES (nombre, FALSE);
        RETURN;
    EXCEPTION WHEN OTHERS THEN
        INSERT INTO _v26_resultado VALUES (nombre, SQLSTATE = sqlstate_esperado);
        IF SQLSTATE <> sqlstate_esperado THEN
            RAISE WARNING '% falló con % (esperado %): %', nombre, SQLSTATE, sqlstate_esperado, SQLERRM;
        END IF;
    END;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION pg_temp.ok(nombre TEXT, condicion BOOLEAN) RETURNS VOID AS $$
    INSERT INTO _v26_resultado VALUES (nombre, coalesce(condicion, FALSE));
$$ LANGUAGE sql;

-- ---------- Marca ----------
SELECT pg_temp.ok('01_sin_identidad_marcados_incompletos', (
    SELECT bool_and(perfil_incompleto) FROM usuarios WHERE numero_cedula_hash IS NULL));
SELECT pg_temp.ok('02_con_identidad_no_marcado', (
    SELECT NOT perfil_incompleto FROM usuarios WHERE email = 'cliente-completo@v26.invalid'));
SELECT pg_temp.ok('03_marca_en_todos_los_roles_y_estados', (
    SELECT count(DISTINCT rol) = 3 AND count(DISTINCT estado) >= 4
      FROM usuarios WHERE perfil_incompleto AND email LIKE '%@v26.invalid'));

-- ---------- No inventar datos ----------
SELECT pg_temp.ok('04_no_inventa_identidad', (
    SELECT count(*) = 1 FROM usuarios WHERE numero_cedula_hash IS NOT NULL OR numero_cedula_cifrado IS NOT NULL
                                         OR fecha_nacimiento_cifrada IS NOT NULL));
SELECT pg_temp.ok('05_ninguna_columna_previa_cambia', NOT EXISTS (
    SELECT 1 FROM usuarios u JOIN _v26_foto_usuarios f ON f.id = u.id
     WHERE to_jsonb(u) - 'perfil_incompleto' IS DISTINCT FROM f.fila));
SELECT pg_temp.ok('06_no_desaparecen_ni_aparecen_usuarios', (
    SELECT (SELECT count(*) FROM usuarios) = (SELECT count(*) FROM _v26_foto_usuarios)));

-- ---------- No bloquear la sesión ----------
SELECT pg_temp.ok('07_sesiones_intactas', NOT EXISTS (
    SELECT 1 FROM sesiones_usuario s JOIN _v26_foto_sesiones f ON f.id = s.id
     WHERE to_jsonb(s) IS DISTINCT FROM f.fila));
SELECT pg_temp.ok('08_ninguna_sesion_revocada', NOT EXISTS (
    SELECT 1 FROM sesiones_usuario s JOIN usuarios u ON u.id = s.usuario_id
     WHERE u.email LIKE '%@v26.invalid' AND s.revoked_at IS NOT NULL));
SELECT pg_temp.ok('09_estado_y_activo_sin_cambios', NOT EXISTS (
    SELECT 1 FROM usuarios u JOIN _v26_foto_usuarios f ON f.id = u.id
     WHERE u.estado IS DISTINCT FROM f.fila->>'estado' OR u.activo IS DISTINCT FROM (f.fila->>'activo')::boolean));

-- ---------- La marca no se puede escribir ni falsear (428C9 = generated_always) ----------
SELECT pg_temp.debe_fallar('10_update_manual_rechazado', $s$
    UPDATE usuarios SET perfil_incompleto = FALSE WHERE email = 'cliente-activo@v26.invalid'$s$, '428C9');
SELECT pg_temp.debe_fallar('11_insert_con_marca_falsa_rechazado', $s$
    INSERT INTO usuarios (nombre, email, password, rol, estado, acepto_terminos, fecha_aceptacion_terminos, perfil_incompleto)
    VALUES ('X', 'falso@v26.invalid', 'x', 'CLIENTE', 'ACTIVO', TRUE, now(), FALSE)$s$, '428C9');

-- ---------- La marca se apaga sola al completar y alcanza a los usuarios nuevos ----------
UPDATE usuarios
   SET numero_cedula_cifrado = 'v1:' || translate(encode(sha256('otro-c'::bytea), 'base64'), '+/=', '-_'),
       numero_cedula_hash = encode(sha256('otro-h'::bytea), 'hex'),
       fecha_nacimiento_cifrada = 'v1:' || translate(encode(sha256('otro-f'::bytea), 'base64'), '+/=', '-_'),
       datos_identidad_bloqueados = TRUE, fecha_bloqueo_identidad = now()
 WHERE email = 'cliente-activo@v26.invalid';
SELECT pg_temp.ok('12_completar_apaga_la_marca', (
    SELECT NOT perfil_incompleto FROM usuarios WHERE email = 'cliente-activo@v26.invalid'));

INSERT INTO usuarios (nombre, email, password, rol, estado, acepto_terminos, fecha_aceptacion_terminos)
VALUES ('Cliente Nuevo', 'cliente-nuevo@v26.invalid', 'no-es-un-hash', 'CLIENTE', 'PENDIENTE_VERIFICACION', TRUE, now());
SELECT pg_temp.ok('13_usuario_nuevo_sin_identidad_marcado', (
    SELECT perfil_incompleto FROM usuarios WHERE email = 'cliente-nuevo@v26.invalid'));

-- La inmutabilidad de V24 sigue vigente sobre los datos ya completados.
SELECT pg_temp.debe_fallar('14_inmutabilidad_v24_vigente', $s$
    UPDATE usuarios SET numero_cedula_hash = NULL, numero_cedula_cifrado = NULL, fecha_nacimiento_cifrada = NULL
     WHERE email = 'cliente-completo@v26.invalid'$s$, '23514');

-- ---------- Vista: solo CLIENTE debe completar en el login, con datos mínimos ----------
SELECT pg_temp.ok('15_vista_solo_cliente_debe_completar', NOT EXISTS (
    SELECT 1 FROM v_usuarios_perfil_incompleto WHERE debe_completar_en_login <> (rol = 'CLIENTE')));
SELECT pg_temp.ok('16_vista_internos_marcados_sin_forzar', (
    SELECT count(*) = 2 AND bool_and(NOT debe_completar_en_login)
      FROM v_usuarios_perfil_incompleto v JOIN usuarios u ON u.id = v.id_usuario
     WHERE u.email IN ('operador@v26.invalid', 'conductor@v26.invalid')));
SELECT pg_temp.ok('17_vista_excluye_completos', NOT EXISTS (
    SELECT 1 FROM v_usuarios_perfil_incompleto v JOIN usuarios u ON u.id = v.id_usuario
     WHERE u.email IN ('cliente-completo@v26.invalid', 'cliente-activo@v26.invalid')));
SELECT pg_temp.ok('18_vista_sin_datos_personales', (
    SELECT array_agg(column_name::text ORDER BY column_name) = ARRAY['debe_completar_en_login', 'id_usuario', 'rol']
      FROM information_schema.columns WHERE table_name = 'v_usuarios_perfil_incompleto'));
-- La vista es de solo lectura: no puede usarse para cambiar el rol (escalada), ni borrar ni insertar usuarios.
SELECT pg_temp.debe_fallar('19_vista_no_permite_cambiar_rol', $s$
    UPDATE v_usuarios_perfil_incompleto SET rol = 'OPERADOR'$s$, '0A000');
SELECT pg_temp.debe_fallar('19b_vista_no_permite_borrar', $s$
    DELETE FROM v_usuarios_perfil_incompleto$s$, '0A000');
SELECT pg_temp.debe_fallar('19c_vista_no_permite_insertar', $s$
    INSERT INTO v_usuarios_perfil_incompleto (id_usuario, rol) VALUES (-1, 'CLIENTE')$s$, '0A000');
SELECT pg_temp.ok('19d_roles_intactos_tras_intentos', NOT EXISTS (
    SELECT 1 FROM usuarios u JOIN _v26_foto_usuarios f ON f.id = u.id WHERE u.rol IS DISTINCT FROM f.fila->>'rol'));

-- ---------- Índice de apoyo para el login ----------
SELECT pg_temp.ok('20_indice_parcial_clientes', EXISTS (
    SELECT 1 FROM pg_indexes WHERE indexname = 'idx_usuarios_clientes_perfil_incompleto'
       AND indexdef LIKE '%perfil_incompleto%' AND indexdef LIKE '%CLIENTE%'));

-- ---------- Orden de reversión: V24 no se puede revertir mientras V26 depende de ella (2BP01) ----------
SELECT pg_temp.debe_fallar('21_rollback_v24_bloqueado_por_v26', $s$
    ALTER TABLE usuarios DROP COLUMN numero_cedula_hash$s$, '2BP01');

\o
SELECT prueba, CASE WHEN ok THEN 'OK' ELSE 'FALLA' END AS resultado FROM _v26_resultado ORDER BY prueba;
DO $$
DECLARE fallidas INT; total INT;
BEGIN
    SELECT count(*) FILTER (WHERE NOT ok), count(*) INTO fallidas, total FROM _v26_resultado;
    IF fallidas > 0 OR total < 24 THEN
        RAISE EXCEPTION 'Pruebas V26 fallidas: % (ejecutadas: %)', fallidas, total;
    END IF;
END;
$$;
ROLLBACK;

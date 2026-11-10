-- Pruebas de V24 (HU-13 / 13-DB-01). Las ejecuta scripts/probar-migracion.sh 24 sobre una COPIA de la BD.
-- Todos los datos son sintéticos: los "cifrados" y hashes son cadenas de relleno con el formato esperado,
-- no corresponden a ninguna cédula real ni a ninguna clave.
\set ON_ERROR_STOP on
\o /dev/null

CREATE TEMP TABLE _v24_resultado (prueba TEXT PRIMARY KEY, ok BOOLEAN NOT NULL);

CREATE OR REPLACE FUNCTION pg_temp.cifrado_sintetico(semilla TEXT) RETURNS TEXT AS $$
    SELECT 'v1:' || translate(encode(sha256(convert_to(semilla, 'UTF8')), 'base64'), '+/=', '-_')
$$ LANGUAGE sql;
CREATE OR REPLACE FUNCTION pg_temp.hash_sintetico(semilla TEXT) RETURNS TEXT AS $$
    SELECT encode(sha256(convert_to('hash:' || semilla, 'UTF8')), 'hex')
$$ LANGUAGE sql;

-- Ejecuta una sentencia que DEBE fallar con el SQLSTATE esperado. Si no falla, la prueba se marca en rojo.
CREATE OR REPLACE FUNCTION pg_temp.debe_fallar(nombre TEXT, sentencia TEXT, sqlstate_esperado TEXT)
RETURNS VOID AS $$
BEGIN
    BEGIN
        EXECUTE sentencia;
        INSERT INTO _v24_resultado VALUES (nombre, FALSE);
        RETURN;
    EXCEPTION WHEN OTHERS THEN
        INSERT INTO _v24_resultado VALUES (nombre, SQLSTATE = sqlstate_esperado);
        IF SQLSTATE <> sqlstate_esperado THEN
            RAISE WARNING '% falló con % (esperado %): %', nombre, SQLSTATE, sqlstate_esperado, SQLERRM;
        END IF;
    END;
END;
$$ LANGUAGE plpgsql;

-- 1. Los usuarios existentes migran sin datos de identidad y sin bloquear.
INSERT INTO _v24_resultado
SELECT '01_usuarios_existentes_sin_identidad', bool_and(numero_cedula_cifrado IS NULL AND numero_cedula_hash IS NULL
       AND fecha_nacimiento_cifrada IS NULL AND datos_identidad_bloqueados = FALSE AND fecha_bloqueo_identidad IS NULL)
FROM usuarios WHERE email LIKE '%@v24.invalid';

-- 2. Registro válido de los tres datos y el bloqueo (camino feliz).
UPDATE usuarios SET numero_cedula_cifrado = pg_temp.cifrado_sintetico('c-a'),
                    numero_cedula_hash = pg_temp.hash_sintetico('a'),
                    fecha_nacimiento_cifrada = pg_temp.cifrado_sintetico('f-a'),
                    datos_identidad_bloqueados = TRUE,
                    fecha_bloqueo_identidad = (now() AT TIME ZONE 'UTC')
 WHERE email = 'legado-a@v24.invalid';
INSERT INTO _v24_resultado SELECT '02_registro_valido', datos_identidad_bloqueados
FROM usuarios WHERE email = 'legado-a@v24.invalid';

-- 3. La unicidad por índice ciego rechaza la misma cédula en otro usuario (23505 = unique_violation).
SELECT pg_temp.debe_fallar('03_cedula_duplicada_rechazada', format($s$
    UPDATE usuarios SET numero_cedula_cifrado = %L, numero_cedula_hash = %L, fecha_nacimiento_cifrada = %L,
           datos_identidad_bloqueados = TRUE, fecha_bloqueo_identidad = now()
     WHERE email = 'legado-b@v24.invalid'$s$,
    pg_temp.cifrado_sintetico('c-otra'), pg_temp.hash_sintetico('a'), pg_temp.cifrado_sintetico('f-b')), '23505');

-- 4. Una cédula en claro no pasa el CHECK de formato (23514 = check_violation).
SELECT pg_temp.debe_fallar('04_cedula_en_claro_rechazada', format($s$
    UPDATE usuarios SET numero_cedula_cifrado = '1000000001', numero_cedula_hash = %L,
           fecha_nacimiento_cifrada = %L, datos_identidad_bloqueados = TRUE, fecha_bloqueo_identidad = now()
     WHERE email = 'legado-b@v24.invalid'$s$, pg_temp.hash_sintetico('b'), pg_temp.cifrado_sintetico('f-b')), '23514');

-- 5. Una fecha de nacimiento en claro tampoco pasa.
SELECT pg_temp.debe_fallar('05_fecha_en_claro_rechazada', format($s$
    UPDATE usuarios SET numero_cedula_cifrado = %L, numero_cedula_hash = %L,
           fecha_nacimiento_cifrada = '1990-01-01', datos_identidad_bloqueados = TRUE, fecha_bloqueo_identidad = now()
     WHERE email = 'legado-b@v24.invalid'$s$, pg_temp.cifrado_sintetico('c-b'), pg_temp.hash_sintetico('b')), '23514');

-- 6. No se admiten datos parciales (cédula sin hash ni fecha).
SELECT pg_temp.debe_fallar('06_datos_parciales_rechazados', format($s$
    UPDATE usuarios SET numero_cedula_cifrado = %L WHERE email = 'legado-b@v24.invalid'$s$,
    pg_temp.cifrado_sintetico('c-b')), '23514');

-- 7. No se admiten datos sin bloquear, ni un bloqueo sin fecha.
SELECT pg_temp.debe_fallar('07_datos_sin_bloqueo_rechazados', format($s$
    UPDATE usuarios SET numero_cedula_cifrado = %L, numero_cedula_hash = %L, fecha_nacimiento_cifrada = %L
     WHERE email = 'legado-b@v24.invalid'$s$,
    pg_temp.cifrado_sintetico('c-b'), pg_temp.hash_sintetico('b'), pg_temp.cifrado_sintetico('f-b')), '23514');
SELECT pg_temp.debe_fallar('08_bloqueo_sin_fecha_rechazado', format($s$
    UPDATE usuarios SET numero_cedula_cifrado = %L, numero_cedula_hash = %L, fecha_nacimiento_cifrada = %L,
           datos_identidad_bloqueados = TRUE
     WHERE email = 'legado-b@v24.invalid'$s$,
    pg_temp.cifrado_sintetico('c-b'), pg_temp.hash_sintetico('b'), pg_temp.cifrado_sintetico('f-b')), '23514');

-- 8. El hash debe ser hex en minúsculas de 64 caracteres. No se acepta, por ejemplo, la cédula sin cifrar.
SELECT pg_temp.debe_fallar('09_hash_con_formato_invalido', format($s$
    UPDATE usuarios SET numero_cedula_cifrado = %L, numero_cedula_hash = '1000000001',
           fecha_nacimiento_cifrada = %L, datos_identidad_bloqueados = TRUE, fecha_bloqueo_identidad = now()
     WHERE email = 'legado-b@v24.invalid'$s$, pg_temp.cifrado_sintetico('c-b'), pg_temp.cifrado_sintetico('f-b')), '23514');

-- 9-12. CA-3: el trigger impide modificar, borrar o desbloquear los datos bloqueados.
SELECT pg_temp.debe_fallar('10_modificar_cedula_bloqueada', format($s$
    UPDATE usuarios SET numero_cedula_cifrado = %L, numero_cedula_hash = %L WHERE email = 'legado-a@v24.invalid'$s$,
    pg_temp.cifrado_sintetico('c-nueva'), pg_temp.hash_sintetico('nueva')), '23514');
SELECT pg_temp.debe_fallar('11_modificar_fecha_bloqueada', format($s$
    UPDATE usuarios SET fecha_nacimiento_cifrada = %L WHERE email = 'legado-a@v24.invalid'$s$,
    pg_temp.cifrado_sintetico('f-nueva')), '23514');
SELECT pg_temp.debe_fallar('12_desbloquear_rechazado', $s$
    UPDATE usuarios SET datos_identidad_bloqueados = FALSE, fecha_bloqueo_identidad = NULL,
           numero_cedula_cifrado = NULL, numero_cedula_hash = NULL, fecha_nacimiento_cifrada = NULL
     WHERE email = 'legado-a@v24.invalid'$s$, '23514');
SELECT pg_temp.debe_fallar('13_cambiar_fecha_bloqueo_rechazado', $s$
    UPDATE usuarios SET fecha_bloqueo_identidad = TIMESTAMP '2000-01-01 00:00:00'
     WHERE email = 'legado-a@v24.invalid'$s$, '23514');

-- 13. Los demás campos del usuario se pueden seguir editando.
UPDATE usuarios SET telefono = '3000000000' WHERE email = 'legado-a@v24.invalid';
INSERT INTO _v24_resultado SELECT '14_otros_campos_editables', telefono = '3000000000'
FROM usuarios WHERE email = 'legado-a@v24.invalid';

-- 14. La rotación de claves permite recifrar con el flag de sesión, pero sigue sin permitir desbloquear.
BEGIN;
SET LOCAL app.rotacion_claves_identidad = 'on';
UPDATE usuarios SET numero_cedula_cifrado = pg_temp.cifrado_sintetico('c-a-rotada'),
                    numero_cedula_hash = pg_temp.hash_sintetico('a-rotada'),
                    fecha_nacimiento_cifrada = pg_temp.cifrado_sintetico('f-a-rotada')
 WHERE email = 'legado-a@v24.invalid';
SELECT pg_temp.debe_fallar('15_rotacion_no_desbloquea', $s$
    UPDATE usuarios SET datos_identidad_bloqueados = FALSE, fecha_bloqueo_identidad = NULL,
           numero_cedula_cifrado = NULL, numero_cedula_hash = NULL, fecha_nacimiento_cifrada = NULL
     WHERE email = 'legado-a@v24.invalid'$s$, '23514');
COMMIT;
INSERT INTO _v24_resultado SELECT '16_rotacion_recifra', numero_cedula_hash = pg_temp.hash_sintetico('a-rotada')
FROM usuarios WHERE email = 'legado-a@v24.invalid';

-- 15. Fuera de esa transacción, el flag de rotación ya no está activo.
SELECT pg_temp.debe_fallar('17_rotacion_no_persiste', format($s$
    UPDATE usuarios SET numero_cedula_hash = %L WHERE email = 'legado-a@v24.invalid'$s$,
    pg_temp.hash_sintetico('otra')), '23514');

-- 16. Varios usuarios pueden seguir sin cédula (NULL no rompe la unicidad).
INSERT INTO _v24_resultado SELECT '18_varios_null_permitidos', count(*) >= 2
FROM usuarios WHERE numero_cedula_hash IS NULL;

-- 17. Ninguna columna de la BD guarda la cédula sintética de la prueba 4 en claro.
INSERT INTO _v24_resultado SELECT '19_sin_cedula_en_claro', NOT EXISTS (
    SELECT 1 FROM usuarios WHERE numero_cedula_cifrado = '1000000001' OR numero_cedula_hash = '1000000001');

-- Resumen. Si alguna prueba falla, el script termina con error.
\o
SELECT prueba, CASE WHEN ok THEN 'OK' ELSE 'FALLA' END AS resultado FROM _v24_resultado ORDER BY prueba;
DO $$
DECLARE fallidas INT;
BEGIN
    SELECT count(*) INTO fallidas FROM _v24_resultado WHERE NOT ok;
    IF fallidas > 0 OR (SELECT count(*) FROM _v24_resultado) < 19 THEN
        RAISE EXCEPTION 'Pruebas V24 fallidas: % (ejecutadas: %)', fallidas, (SELECT count(*) FROM _v24_resultado);
    END IF;
END;
$$;

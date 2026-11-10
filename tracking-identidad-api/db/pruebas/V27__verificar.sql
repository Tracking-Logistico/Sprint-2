-- Pruebas de V27 (HU-13 CA-7). Las ejecuta scripts/probar-migracion.sh 27 sobre una COPIA de la BD.
-- Todo corre en una transacción que termina en ROLLBACK: las pruebas no dejan datos. Todos los datos son sintéticos.
\set ON_ERROR_STOP on
\o /dev/null
BEGIN;

CREATE TEMP TABLE _v27_resultado (prueba TEXT PRIMARY KEY, ok BOOLEAN NOT NULL);

CREATE FUNCTION pg_temp.debe_fallar(nombre TEXT, sentencia TEXT, sqlstate_esperado TEXT) RETURNS VOID AS $$
BEGIN
    BEGIN
        EXECUTE sentencia;
        INSERT INTO _v27_resultado VALUES (nombre, FALSE);
        RETURN;
    EXCEPTION WHEN OTHERS THEN
        INSERT INTO _v27_resultado VALUES (nombre, SQLSTATE = sqlstate_esperado);
        IF SQLSTATE <> sqlstate_esperado THEN
            RAISE WARNING '% falló con % (esperado %): %', nombre, SQLSTATE, sqlstate_esperado, SQLERRM;
        END IF;
    END;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION pg_temp.ok(nombre TEXT, condicion BOOLEAN) RETURNS VOID AS $$
    INSERT INTO _v27_resultado VALUES (nombre, coalesce(condicion, FALSE));
$$ LANGUAGE sql;

CREATE FUNCTION pg_temp.usuario(correo TEXT) RETURNS BIGINT AS $$
    SELECT id FROM usuarios WHERE email = correo
$$ LANGUAGE sql;

-- Envío 18+ en reparto, asignado al conductor 1.
CREATE FUNCTION pg_temp.envio_18(numero TEXT) RETURNS BIGINT AS $$
DECLARE nuevo BIGINT;
BEGIN
    INSERT INTO pedidos (numero_pedido, id_cliente, direccion_origen, direccion_destino, descripcion_paquete,
                         peso_kg, largo_cm, ancho_cm, alto_cm, tipo_servicio, prioridad_sugerida, estado,
                         requiere_verificacion_edad)
    SELECT numero, cl.id_cliente, 'Origen sintetico', 'Destino sintetico', 'Paquete sintetico',
           1, 10, 10, 10, 'ESTANDAR', 'MEDIA', 'SOLICITADO', TRUE
      FROM clientes cl WHERE cl.id_usuario = pg_temp.usuario('cliente@v27.invalid')
    RETURNING id_pedido INTO nuevo;
    UPDATE pedidos SET estado = 'EN_REPARTO' WHERE id_pedido = nuevo;
    INSERT INTO paradas_ruta (id_ruta, id_pedido, orden, estado)
    SELECT r.id_ruta, nuevo, 1, 'PENDIENTE' FROM rutas r JOIN conductores c ON c.id_conductor = r.id_conductor
     WHERE c.id_usuario = pg_temp.usuario('conductor-1@v27.invalid');
    RETURN nuevo;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION pg_temp.verificar(pedido BIGINT, resultado TEXT) RETURNS VOID AS $$
    INSERT INTO verificacion_edad_log (id_pedido, id_usuario_conductor, resultado)
    VALUES (pedido, pg_temp.usuario('conductor-1@v27.invalid'), resultado);
$$ LANGUAGE sql;

-- 1. Con NO_CUMPLE registrado, el envío pasa al estado nuevo (camino de CA-7).
SELECT pg_temp.verificar(pg_temp.envio_18('V27-NO'), 'NO_CUMPLE');
UPDATE pedidos SET estado = 'ENTREGA_FALLIDA_VERIFICACION_EDAD' WHERE numero_pedido = 'V27-NO';
SELECT pg_temp.ok('01_no_cumple_permite_estado_nuevo', (SELECT estado = 'ENTREGA_FALLIDA_VERIFICACION_EDAD'
                                                         FROM pedidos WHERE numero_pedido = 'V27-NO'));

-- 2. Sin resultado registrado, el estado se rechaza.
SELECT pg_temp.envio_18('V27-SIN');
SELECT pg_temp.debe_fallar('02_sin_log_rechazado', $s$
    UPDATE pedidos SET estado = 'ENTREGA_FALLIDA_VERIFICACION_EDAD' WHERE numero_pedido = 'V27-SIN'$s$, '23514');

-- 3. Con CUMPLE (mayor de edad verificado), tampoco: ese envío se debe poder entregar, no fallar por edad.
SELECT pg_temp.verificar(pg_temp.envio_18('V27-SI'), 'CUMPLE');
SELECT pg_temp.debe_fallar('03_con_cumple_rechazado', $s$
    UPDATE pedidos SET estado = 'ENTREGA_FALLIDA_VERIFICACION_EDAD' WHERE numero_pedido = 'V27-SI'$s$, '23514');

-- 4. Nadie puede insertar un envío directamente en el estado nuevo.
SELECT pg_temp.debe_fallar('04_insert_directo_rechazado', $s$
    INSERT INTO pedidos (numero_pedido, id_cliente, direccion_origen, direccion_destino, descripcion_paquete,
                         peso_kg, largo_cm, ancho_cm, alto_cm, tipo_servicio, prioridad_sugerida, estado)
    SELECT 'V27-DIRECTO', id_cliente, 'o', 'd', 'p', 1, 1, 1, 1, 'ESTANDAR', 'MEDIA',
           'ENTREGA_FALLIDA_VERIFICACION_EDAD' FROM clientes LIMIT 1$s$, '23514');

-- 5. La lista blanca de estados sigue rechazando valores inventados.
SELECT pg_temp.debe_fallar('05_estado_inventado_rechazado', $s$
    UPDATE pedidos SET estado = 'ENTREGA_FALLIDA_EDAD' WHERE numero_pedido = 'V27-SIN'$s$, '23514');

-- 6. Los estados anteriores siguen siendo válidos (sin regresión de V22).
UPDATE pedidos SET estado = 'PAUSADO' WHERE numero_pedido = 'V27-LEGADO';
UPDATE pedidos SET estado = 'CANCELADO' WHERE numero_pedido = 'V27-LEGADO';
SELECT pg_temp.ok('06_estados_previos_validos', (SELECT estado = 'CANCELADO' FROM pedidos WHERE numero_pedido = 'V27-LEGADO'));

-- 7. Con CUMPLE, la entrega sigue disponible (V25), y el estado nuevo no la bloquea.
UPDATE pedidos SET estado = 'ENTREGADO' WHERE numero_pedido = 'V27-SI';
SELECT pg_temp.ok('07_cumple_se_entrega', (SELECT estado = 'ENTREGADO' FROM pedidos WHERE numero_pedido = 'V27-SI'));

-- 8. El envío fallido por edad no se puede entregar (V25: ENTREGADO exige CUMPLE).
SELECT pg_temp.debe_fallar('08_fallido_por_edad_no_se_entrega', $s$
    UPDATE pedidos SET estado = 'ENTREGADO' WHERE numero_pedido = 'V27-NO'$s$, '23514');

-- 9. Ningún estado admitido supera el tamaño de la columna.
SELECT pg_temp.ok('09_columna_admite_estado_nuevo', (
    SELECT character_maximum_length >= length('ENTREGA_FALLIDA_VERIFICACION_EDAD')
      FROM information_schema.columns WHERE table_name = 'pedidos' AND column_name = 'estado'));

\o
SELECT prueba, CASE WHEN ok THEN 'OK' ELSE 'FALLA' END AS resultado FROM _v27_resultado ORDER BY prueba;
DO $$
DECLARE fallidas INT; total INT;
BEGIN
    SELECT count(*) FILTER (WHERE NOT ok), count(*) INTO fallidas, total FROM _v27_resultado;
    IF fallidas > 0 OR total < 9 THEN
        RAISE EXCEPTION 'Pruebas V27 fallidas: % (ejecutadas: %)', fallidas, total;
    END IF;
END;
$$;
ROLLBACK;

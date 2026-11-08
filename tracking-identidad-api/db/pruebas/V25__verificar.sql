-- Pruebas de V25 (HU-13 / 13-DB-02). Las ejecuta scripts/probar-migracion.sh 25 sobre una COPIA de la BD.
-- Todo corre en una transacción que termina en ROLLBACK: las pruebas no dejan datos. Todos los datos son sintéticos.
\set ON_ERROR_STOP on
\o /dev/null
BEGIN;

CREATE TEMP TABLE _v25_resultado (prueba TEXT PRIMARY KEY, ok BOOLEAN NOT NULL);

CREATE FUNCTION pg_temp.debe_fallar(nombre TEXT, sentencia TEXT, sqlstate_esperado TEXT) RETURNS VOID AS $$
BEGIN
    BEGIN
        EXECUTE sentencia;
        INSERT INTO _v25_resultado VALUES (nombre, FALSE);
        RETURN;
    EXCEPTION WHEN OTHERS THEN
        INSERT INTO _v25_resultado VALUES (nombre, SQLSTATE = sqlstate_esperado);
        IF SQLSTATE <> sqlstate_esperado THEN
            RAISE WARNING '% falló con % (esperado %): %', nombre, SQLSTATE, sqlstate_esperado, SQLERRM;
        END IF;
    END;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION pg_temp.ok(nombre TEXT, condicion BOOLEAN) RETURNS VOID AS $$
    INSERT INTO _v25_resultado VALUES (nombre, coalesce(condicion, FALSE));
$$ LANGUAGE sql;

CREATE FUNCTION pg_temp.usuario(correo TEXT) RETURNS BIGINT AS $$
    SELECT id FROM usuarios WHERE email = correo
$$ LANGUAGE sql;

-- Crea un envío como lo haría el remitente (en SOLICITADO) y luego lo avanza al estado pedido.
CREATE FUNCTION pg_temp.envio(numero TEXT, estado_final TEXT, requiere BOOLEAN) RETURNS BIGINT AS $$
DECLARE nuevo BIGINT;
BEGIN
    INSERT INTO pedidos (numero_pedido, id_cliente, direccion_origen, direccion_destino, descripcion_paquete,
                         peso_kg, largo_cm, ancho_cm, alto_cm, tipo_servicio, prioridad_sugerida, estado,
                         requiere_verificacion_edad)
    SELECT numero, cl.id_cliente, 'Origen sintetico', 'Destino sintetico', 'Paquete sintetico',
           1, 10, 10, 10, 'ESTANDAR', 'MEDIA', 'SOLICITADO', requiere
      FROM clientes cl WHERE cl.id_usuario = pg_temp.usuario('cliente@v25.invalid')
    RETURNING id_pedido INTO nuevo;
    IF estado_final <> 'SOLICITADO' THEN
        UPDATE pedidos SET estado = estado_final WHERE id_pedido = nuevo;
    END IF;
    RETURN nuevo;
END;
$$ LANGUAGE plpgsql;

-- Asigna el envío a la ruta del conductor indicado, como una parada con el estado indicado.
CREATE FUNCTION pg_temp.parada(p_pedido BIGINT, p_correo TEXT, p_estado TEXT) RETURNS VOID AS $$
    INSERT INTO paradas_ruta (id_ruta, id_pedido, orden, estado)
    SELECT r.id_ruta, p_pedido, 1, p_estado FROM rutas r JOIN conductores c ON c.id_conductor = r.id_conductor
     WHERE c.id_usuario = pg_temp.usuario(p_correo);
$$ LANGUAGE sql;

CREATE FUNCTION pg_temp.verificar(pedido BIGINT, correo TEXT, resultado TEXT) RETURNS TEXT AS $$
    SELECT format('INSERT INTO verificacion_edad_log (id_pedido, id_usuario_conductor, resultado) VALUES (%s, %s, %L)',
                  pedido, coalesce(pg_temp.usuario(correo)::text, 'NULL'), resultado)
$$ LANGUAGE sql;

-- ---------- pedidos.requiere_verificacion_edad (CA-4) ----------
SELECT pg_temp.ok('01_envios_previos_sin_18', (SELECT NOT requiere_verificacion_edad FROM pedidos
                                                WHERE numero_pedido = 'V25-LEGADO'));

SELECT pg_temp.envio('V25-SOL', 'SOLICITADO', TRUE);
SELECT pg_temp.ok('02_remitente_marca_al_solicitar', (SELECT requiere_verificacion_edad FROM pedidos
                                                       WHERE numero_pedido = 'V25-SOL'));

UPDATE pedidos SET requiere_verificacion_edad = FALSE WHERE numero_pedido = 'V25-SOL';
UPDATE pedidos SET estado = 'CORRECCION_SOLICITADA' WHERE numero_pedido = 'V25-SOL';
UPDATE pedidos SET requiere_verificacion_edad = TRUE WHERE numero_pedido = 'V25-SOL';
SELECT pg_temp.ok('03_editable_en_solicitado_y_correccion', (SELECT requiere_verificacion_edad FROM pedidos
                                                              WHERE numero_pedido = 'V25-SOL'));

SELECT pg_temp.debe_fallar('04_cambio_al_validar_rechazado', $s$
    UPDATE pedidos SET estado = 'CREADO', requiere_verificacion_edad = FALSE WHERE numero_pedido = 'V25-SOL'$s$, '23514');

SELECT pg_temp.envio('V25-CREADO', 'CREADO', TRUE);
SELECT pg_temp.debe_fallar('05_desactivar_tras_validacion_rechazado', $s$
    UPDATE pedidos SET requiere_verificacion_edad = FALSE WHERE numero_pedido = 'V25-CREADO'$s$, '23514');

SELECT pg_temp.envio('V25-REPARTO-NO18', 'EN_REPARTO', FALSE);
SELECT pg_temp.debe_fallar('06_activar_en_reparto_rechazado', $s$
    UPDATE pedidos SET requiere_verificacion_edad = TRUE WHERE numero_pedido = 'V25-REPARTO-NO18'$s$, '23514');

-- Al aprobar, Pedido.validar deja el envío en SOLICITADO pero registra fecha_validacion: a partir de ahí, inmutable.
SELECT pg_temp.envio('V25-VALIDADO', 'SOLICITADO', TRUE);
SELECT pg_temp.debe_fallar('06b_cambio_en_la_misma_validacion_rechazado', $s$
    UPDATE pedidos SET fecha_validacion = now(), requiere_verificacion_edad = FALSE
     WHERE numero_pedido = 'V25-VALIDADO'$s$, '23514');
UPDATE pedidos SET fecha_validacion = now() WHERE numero_pedido = 'V25-VALIDADO';
SELECT pg_temp.debe_fallar('06c_cambio_tras_validar_en_solicitado_rechazado', $s$
    UPDATE pedidos SET requiere_verificacion_edad = FALSE WHERE numero_pedido = 'V25-VALIDADO'$s$, '23514');

-- Ciclo de corrección: solicitarCorreccion (con fecha) -> el remitente cambia el flag -> corregir (fecha NULL).
SELECT pg_temp.envio('V25-CORR', 'SOLICITADO', FALSE);
UPDATE pedidos SET estado = 'CORRECCION_SOLICITADA', fecha_validacion = now() WHERE numero_pedido = 'V25-CORR';
UPDATE pedidos SET requiere_verificacion_edad = TRUE WHERE numero_pedido = 'V25-CORR';
UPDATE pedidos SET estado = 'SOLICITADO', fecha_validacion = NULL WHERE numero_pedido = 'V25-CORR';
SELECT pg_temp.ok('06d_ciclo_de_correccion_permitido', (SELECT requiere_verificacion_edad AND fecha_validacion IS NULL
                                                         FROM pedidos WHERE numero_pedido = 'V25-CORR'));

SELECT pg_temp.debe_fallar('07_alta_18_ya_validada_rechazada', $s$
    INSERT INTO pedidos (numero_pedido, id_cliente, direccion_origen, direccion_destino, descripcion_paquete,
                         peso_kg, largo_cm, ancho_cm, alto_cm, tipo_servicio, prioridad_sugerida, estado,
                         requiere_verificacion_edad)
    SELECT 'V25-DEV', id_cliente, 'o', 'd', 'p', 1, 1, 1, 1, 'ESTANDAR', 'MEDIA', 'CREADO', TRUE FROM clientes
     LIMIT 1$s$, '23514');

UPDATE pedidos SET estado = 'RECIBIDO_EN_ORIGEN', descripcion_paquete = 'Otra descripcion'
 WHERE numero_pedido = 'V25-CREADO';
SELECT pg_temp.ok('08_otros_cambios_permitidos', (SELECT estado = 'RECIBIDO_EN_ORIGEN' AND requiere_verificacion_edad
                                                   FROM pedidos WHERE numero_pedido = 'V25-CREADO'));

-- ---------- verificacion_edad_log (CA-6) ----------
-- P1: 18+, en reparto, asignado al conductor 1.
SELECT pg_temp.parada(pg_temp.envio('V25-P1', 'EN_REPARTO', TRUE), 'conductor-1@v25.invalid', 'PENDIENTE');
SELECT pg_temp.debe_fallar('09_conductor_ajeno_rechazado',
    pg_temp.verificar((SELECT id_pedido FROM pedidos WHERE numero_pedido = 'V25-P1'), 'conductor-2@v25.invalid', 'CUMPLE'),
    '42501');
SELECT pg_temp.debe_fallar('10_cliente_remitente_rechazado',
    pg_temp.verificar((SELECT id_pedido FROM pedidos WHERE numero_pedido = 'V25-P1'), 'cliente@v25.invalid', 'CUMPLE'),
    '42501');

-- Operador con fila en conductores y con la parada en su ruta: igual lo rechaza, porque su rol no es CONDUCTOR.
SELECT pg_temp.parada(pg_temp.envio('V25-OP', 'EN_REPARTO', TRUE), 'operador@v25.invalid', 'PENDIENTE');
SELECT pg_temp.debe_fallar('11_rol_no_conductor_rechazado',
    pg_temp.verificar((SELECT id_pedido FROM pedidos WHERE numero_pedido = 'V25-OP'), 'operador@v25.invalid', 'CUMPLE'),
    '42501');

DO $$ BEGIN EXECUTE pg_temp.verificar((SELECT id_pedido FROM pedidos WHERE numero_pedido = 'V25-P1'),
                                      'conductor-1@v25.invalid', 'CUMPLE'); END $$;
SELECT pg_temp.ok('12_conductor_asignado_registra', (
    SELECT l.resultado = 'CUMPLE' AND l.id_usuario_conductor = pg_temp.usuario('conductor-1@v25.invalid')
           AND l.fecha_verificacion IS NOT NULL
      FROM verificacion_edad_log l JOIN pedidos p ON p.id_pedido = l.id_pedido WHERE p.numero_pedido = 'V25-P1'));

SELECT pg_temp.debe_fallar('13_segundo_resultado_rechazado',
    pg_temp.verificar((SELECT id_pedido FROM pedidos WHERE numero_pedido = 'V25-P1'), 'conductor-1@v25.invalid', 'NO_CUMPLE'),
    '23505');
SELECT pg_temp.debe_fallar('14_reintento_identico_rechazado',
    pg_temp.verificar((SELECT id_pedido FROM pedidos WHERE numero_pedido = 'V25-P1'), 'conductor-1@v25.invalid', 'CUMPLE'),
    '23505');

-- Envío sin 18+, aunque esté asignado al conductor.
SELECT pg_temp.parada((SELECT id_pedido FROM pedidos WHERE numero_pedido = 'V25-REPARTO-NO18'),
                      'conductor-1@v25.invalid', 'PENDIENTE');
SELECT pg_temp.debe_fallar('15_envio_sin_18_rechazado',
    pg_temp.verificar((SELECT id_pedido FROM pedidos WHERE numero_pedido = 'V25-REPARTO-NO18'), 'conductor-1@v25.invalid', 'CUMPLE'),
    '23514');

-- Envío 18+ que todavía no está en reparto.
SELECT pg_temp.parada((SELECT id_pedido FROM pedidos WHERE numero_pedido = 'V25-CREADO'),
                      'conductor-1@v25.invalid', 'PENDIENTE');
SELECT pg_temp.debe_fallar('16_envio_fuera_de_reparto_rechazado',
    pg_temp.verificar((SELECT id_pedido FROM pedidos WHERE numero_pedido = 'V25-CREADO'), 'conductor-1@v25.invalid', 'CUMPLE'),
    '23514');

-- Reasignación: la parada del conductor 1 se cancela y queda una PENDIENTE del conductor 2.
SELECT pg_temp.envio('V25-REASIG', 'EN_REPARTO', TRUE);
SELECT pg_temp.parada((SELECT id_pedido FROM pedidos WHERE numero_pedido = 'V25-REASIG'), 'conductor-1@v25.invalid', 'CANCELADA');
SELECT pg_temp.parada((SELECT id_pedido FROM pedidos WHERE numero_pedido = 'V25-REASIG'), 'conductor-2@v25.invalid', 'PENDIENTE');
SELECT pg_temp.debe_fallar('17_conductor_anterior_rechazado',
    pg_temp.verificar((SELECT id_pedido FROM pedidos WHERE numero_pedido = 'V25-REASIG'), 'conductor-1@v25.invalid', 'NO_CUMPLE'),
    '42501');
DO $$ BEGIN EXECUTE pg_temp.verificar((SELECT id_pedido FROM pedidos WHERE numero_pedido = 'V25-REASIG'),
                                      'conductor-2@v25.invalid', 'NO_CUMPLE'); END $$;
SELECT pg_temp.ok('18_conductor_actual_registra_no_cumple', EXISTS (
    SELECT 1 FROM verificacion_edad_log l JOIN pedidos p ON p.id_pedido = l.id_pedido
     WHERE p.numero_pedido = 'V25-REASIG' AND l.resultado = 'NO_CUMPLE'));

-- ENTREGA_REPROGRAMADA también cuenta como en entrega.
SELECT pg_temp.parada(pg_temp.envio('V25-REPROG', 'ENTREGA_REPROGRAMADA', TRUE), 'conductor-1@v25.invalid', 'PENDIENTE');
DO $$ BEGIN EXECUTE pg_temp.verificar((SELECT id_pedido FROM pedidos WHERE numero_pedido = 'V25-REPROG'),
                                      'conductor-1@v25.invalid', 'CUMPLE'); END $$;
SELECT pg_temp.ok('19_entrega_reprogramada_admitida', EXISTS (
    SELECT 1 FROM verificacion_edad_log l JOIN pedidos p ON p.id_pedido = l.id_pedido WHERE p.numero_pedido = 'V25-REPROG'));

-- Lista blanca del resultado: valores inventados, en minúsculas o con inyección.
SELECT pg_temp.parada(pg_temp.envio('V25-RES', 'EN_REPARTO', TRUE), 'conductor-1@v25.invalid', 'PENDIENTE');
SELECT pg_temp.debe_fallar('20_resultado_inventado_rechazado',
    pg_temp.verificar((SELECT id_pedido FROM pedidos WHERE numero_pedido = 'V25-RES'), 'conductor-1@v25.invalid', 'APROBADO'), '23514');
SELECT pg_temp.debe_fallar('21_resultado_minusculas_rechazado',
    pg_temp.verificar((SELECT id_pedido FROM pedidos WHERE numero_pedido = 'V25-RES'), 'conductor-1@v25.invalid', 'cumple'), '23514');
SELECT pg_temp.debe_fallar('22_resultado_inyeccion_rechazado',
    pg_temp.verificar((SELECT id_pedido FROM pedidos WHERE numero_pedido = 'V25-RES'), 'conductor-1@v25.invalid',
                      $x$CUMPLE'); DELETE FROM verificacion_edad_log; --$x$), '22001');
-- Conductor inexistente (NULL): lo rechaza el trigger, que se evalúa antes que el NOT NULL.
SELECT pg_temp.debe_fallar('23_conductor_inexistente_rechazado',
    pg_temp.verificar((SELECT id_pedido FROM pedidos WHERE numero_pedido = 'V25-RES'), 'nadie@v25.invalid', 'CUMPLE'), '42501');
SELECT pg_temp.debe_fallar('24_envio_inexistente_rechazado',
    pg_temp.verificar(-1, 'conductor-1@v25.invalid', 'CUMPLE'), '23514');

-- Append-only: el resultado no se corrige, no se borra y no se trunca.
SELECT pg_temp.debe_fallar('25_update_rechazado', $s$
    UPDATE verificacion_edad_log SET resultado = 'CUMPLE' WHERE resultado = 'NO_CUMPLE'$s$, '23514');
SELECT pg_temp.debe_fallar('26_delete_rechazado', $s$DELETE FROM verificacion_edad_log$s$, '23514');
SELECT pg_temp.debe_fallar('27_truncate_rechazado', $s$TRUNCATE verificacion_edad_log$s$, '23514');
-- Sin la parada, la única referencia que queda al envío es su log, con ON DELETE RESTRICT (23001).
DELETE FROM paradas_ruta WHERE id_pedido = (SELECT id_pedido FROM pedidos WHERE numero_pedido = 'V25-P1');
SELECT pg_temp.debe_fallar('28_borrar_envio_con_log_rechazado', $s$
    DELETE FROM pedidos WHERE numero_pedido = 'V25-P1'$s$, '23001');
SELECT pg_temp.ok('29_resultados_intactos', (SELECT count(*) = 3 FROM verificacion_edad_log));

-- CA-6: un envío 18+ solo se entrega con CUMPLE. Un envío sin 18+ se entrega sin log.
SELECT pg_temp.parada(pg_temp.envio('V25-SINVERIF', 'EN_REPARTO', TRUE), 'conductor-1@v25.invalid', 'PENDIENTE');
SELECT pg_temp.debe_fallar('32_entregar_sin_verificacion_rechazado', $s$
    UPDATE pedidos SET estado = 'ENTREGADO' WHERE numero_pedido = 'V25-SINVERIF'$s$, '23514');
SELECT pg_temp.debe_fallar('33_entregar_con_no_cumple_rechazado', $s$
    UPDATE pedidos SET estado = 'ENTREGADO' WHERE numero_pedido = 'V25-REASIG'$s$, '23514');
UPDATE pedidos SET estado = 'ENTREGADO' WHERE numero_pedido = 'V25-REPROG';
SELECT pg_temp.ok('34_entregar_con_cumple_permitido', (SELECT estado = 'ENTREGADO' FROM pedidos
                                                        WHERE numero_pedido = 'V25-REPROG'));
UPDATE pedidos SET estado = 'ENTREGADO' WHERE numero_pedido = 'V25-REPARTO-NO18';
SELECT pg_temp.ok('35_envio_sin_18_se_entrega_sin_log', (SELECT estado = 'ENTREGADO' FROM pedidos
                                                          WHERE numero_pedido = 'V25-REPARTO-NO18'));
-- NO_CUMPLE: el envío puede pasar a entrega fallida o devolución (CA-7).
UPDATE pedidos SET estado = 'ENTREGA_FALLIDA' WHERE numero_pedido = 'V25-REASIG';
SELECT pg_temp.ok('36_no_cumple_permite_entrega_fallida', (SELECT estado = 'ENTREGA_FALLIDA' FROM pedidos
                                                            WHERE numero_pedido = 'V25-REASIG'));

-- Minimización: la tabla tiene exactamente los cuatro campos permitidos (sin documento, fecha de nacimiento ni texto libre).
SELECT pg_temp.ok('30_solo_cuatro_columnas', (
    SELECT array_agg(column_name::text ORDER BY column_name)
           = ARRAY['fecha_verificacion', 'id_pedido', 'id_usuario_conductor', 'resultado']
      FROM information_schema.columns WHERE table_name = 'verificacion_edad_log'));
SELECT pg_temp.ok('31_fecha_con_zona_horaria', (
    SELECT data_type = 'timestamp with time zone' FROM information_schema.columns
     WHERE table_name = 'verificacion_edad_log' AND column_name = 'fecha_verificacion'));

\o
SELECT prueba, CASE WHEN ok THEN 'OK' ELSE 'FALLA' END AS resultado FROM _v25_resultado ORDER BY prueba;
DO $$
DECLARE fallidas INT; total INT;
BEGIN
    SELECT count(*) FILTER (WHERE NOT ok), count(*) INTO fallidas, total FROM _v25_resultado;
    IF fallidas > 0 OR total < 39 THEN
        RAISE EXCEPTION 'Pruebas V25 fallidas: % (ejecutadas: %)', fallidas, total;
    END IF;
END;
$$;
ROLLBACK;

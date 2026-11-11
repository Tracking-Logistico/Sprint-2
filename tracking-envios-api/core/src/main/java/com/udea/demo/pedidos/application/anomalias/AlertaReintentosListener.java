package com.udea.demo.pedidos.application.anomalias;

import com.udea.demo.pedidos.domain.event.IncidenciaRegistradaEvent;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.ZoneOffset;


@Component
@ConditionalOnProperty(name="app.anomalias.enabled",havingValue="true")
public class AlertaReintentosListener {
    private final JdbcTemplate jdbc;
    private final Clock reloj;

    public AlertaReintentosListener(JdbcTemplate jdbc,@Qualifier("relojAnomalias") Clock reloj) {
        this.jdbc=jdbc;
        this.reloj=reloj;
    }

    @EventListener
    @Transactional(propagation=Propagation.MANDATORY)
    public void alRegistrar(IncidenciaRegistradaEvent evento) {
        if (!evento.tipoIncidencia().cuentaComoIntento() || evento.intentosEntregaFallidos()!=2
                || evento.estadoActual().esFinal()) return;
        String origen = "INTENTO-" + evento.incidenciaId();
        Integer existentes = jdbc.queryForObject("""
                SELECT count(*) FROM anomalias WHERE envio_id=? AND tipo='REINTENTOS_ACUMULADOS'
                AND (evento_origen=? OR estado='ACTIVA')
                """, Integer.class, evento.pedidoId(), origen);
        if (existentes != null && existentes > 0) return;
        Long oficina = jdbc.query("""
                SELECT oficina_id FROM eventos_ubicacion_pedido e WHERE id_pedido=? AND oficina_id IS NOT NULL
                AND NOT EXISTS (SELECT 1 FROM eventos_ubicacion_pedido c WHERE c.corrige_evento_id=e.id)
                ORDER BY fecha_evento DESC,id DESC LIMIT 1
                """, (r,n) -> r.getLong(1), evento.pedidoId()).stream().findFirst().orElse(null);
        var ahora = reloj.instant().atOffset(ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO anomalias (envio_id,tipo,evento_origen,oficina_id,inicio_utc,intentos,estado,clave_activa,creada_en)
                VALUES (?,'REINTENTOS_ACUMULADOS',?,?,?,2,'ACTIVA',1,?)
                """, evento.pedidoId(), origen, oficina, ahora, ahora);
    }
}

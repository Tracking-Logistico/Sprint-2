package com.udea.demo.pedidos.application.anomalias;

import com.udea.demo.pedidos.domain.anomalias.*;
import com.udea.demo.pedidos.domain.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.List;
import static com.udea.demo.pedidos.application.anomalias.AnomaliasDTO.Parametros;

@Component
public class ReglasAnomalias {
    private final JdbcTemplate jdbc;
    private final ZoneId zonaHistorica;

    public ReglasAnomalias(JdbcTemplate jdbc,
            @Value("${app.anomalias.zona-historica:UTC}") String zonaHistorica) {
        this.jdbc = jdbc;
        this.zonaHistorica = ZoneId.of(zonaHistorica);
    }

    public CondicionAnomalia evaluar(Pedido pedido, TipoAnomalia tipo, Parametros parametros, Instant ahora) {
        if (pedido.getEstado() == null || !pedido.getEstado().esActivoLogistico()) return null;
        CondicionAnomalia condicion = switch (tipo) {
            case PERMANENCIA_EXCESIVA -> permanencia(pedido, parametros.permanenciaHoras());
            case INACTIVIDAD_REPARTO -> inactividad(pedido, parametros.inactividadHoras());
            case REINTENTOS_ACUMULADOS -> reintentos(pedido);
            case SIN_CONFIRMACION_RECEPCION -> recepcion(pedido, parametros.recepcionHoras());
        };
        return condicion != null && condicion.excedidaEn(ahora) ? condicion : null;
    }

    public record Ubicacion(long id, String etapa, Long oficinaId, LocalDateTime fecha) {}

    public Ubicacion ultimaUbicacion(long envio) {
        return jdbc.query("""
                SELECT e.id,e.etapa,e.oficina_id,e.fecha_evento FROM eventos_ubicacion_pedido e
                WHERE e.id_pedido=? AND NOT EXISTS
                (SELECT 1 FROM eventos_ubicacion_pedido c WHERE c.corrige_evento_id=e.id)
                ORDER BY e.fecha_evento DESC,e.id DESC LIMIT 1
                """, (r,n) -> new Ubicacion(r.getLong(1),r.getString(2),r.getObject(3,Long.class),
                        r.getObject(4,LocalDateTime.class)), envio).stream().findFirst().orElse(null);
    }

    public Long oficinaResponsable(long envio) {
        return jdbc.query("""
                SELECT e.oficina_id FROM eventos_ubicacion_pedido e WHERE e.id_pedido=? AND e.oficina_id IS NOT NULL
                AND NOT EXISTS (SELECT 1 FROM eventos_ubicacion_pedido c WHERE c.corrige_evento_id=e.id)
                ORDER BY e.fecha_evento DESC,e.id DESC LIMIT 1
                """, (r,n) -> r.getLong(1), envio).stream().findFirst().orElse(null);
    }

    private CondicionAnomalia permanencia(Pedido pedido, int horas) {
        Ubicacion actual = ultimaUbicacion(pedido.getId());
        if (actual == null || actual.oficinaId() == null) return null;
        boolean enOrigen = actual.etapa().equals("EN_BODEGA_ORIGEN")
                && pedido.getEstado() == EstadoPedido.RECIBIDO_EN_ORIGEN;
        boolean enDestino = actual.etapa().equals("LLEGADO_OFICINA_DESTINO")
                && pedido.getEstado() == EstadoPedido.EN_TRANSITO;
        if (!enOrigen && !enDestino) return null;

        List<Ubicacion> comienzos = jdbc.query("""
                SELECT e.id,e.etapa,e.oficina_id,e.fecha_evento FROM eventos_ubicacion_pedido e
                WHERE e.id_pedido=? AND e.oficina_id=? AND e.etapa IN ('EN_BODEGA_ORIGEN','LLEGADO_OFICINA_DESTINO')
                AND NOT EXISTS (SELECT 1 FROM eventos_ubicacion_pedido c WHERE c.corrige_evento_id=e.id)
                AND NOT EXISTS (SELECT 1 FROM eventos_ubicacion_pedido m WHERE m.id_pedido=e.id_pedido
                  AND (m.fecha_evento>e.fecha_evento OR (m.fecha_evento=e.fecha_evento AND m.id>e.id))
                  AND (m.oficina_id IS NULL OR m.oficina_id<>e.oficina_id
                       OR m.etapa NOT IN ('EN_BODEGA_ORIGEN','LLEGADO_OFICINA_DESTINO'))
                  AND NOT EXISTS (SELECT 1 FROM eventos_ubicacion_pedido c WHERE c.corrige_evento_id=m.id))
                ORDER BY e.fecha_evento,e.id LIMIT 1
                """, (r,n) -> new Ubicacion(r.getLong(1),r.getString(2),r.getObject(3,Long.class),
                        r.getObject(4,LocalDateTime.class)), pedido.getId(), actual.oficinaId());
        if (comienzos.isEmpty()) return null;
        Ubicacion comienzo = comienzos.get(0);
        return new CondicionAnomalia("OFICINA-"+comienzo.id(),actual.oficinaId(),instante(comienzo.fecha()),horas,null);
    }

    private CondicionAnomalia inactividad(Pedido pedido, int horas) {
        var asignaciones = jdbc.query("""
                SELECT id_parada,fecha_asignacion FROM paradas_ruta WHERE id_pedido=? AND estado='PENDIENTE'
                ORDER BY fecha_asignacion DESC,id_parada DESC LIMIT 1
                """, (r,n) -> new Movimiento("RUTA-"+r.getLong(1),instante(r.getObject(2,LocalDateTime.class))),
                pedido.getId());
        if (asignaciones.isEmpty()) return null;
        Movimiento asignacion = asignaciones.get(0);

        var movimientos = jdbc.query("""
                SELECT clase,identificador,fecha FROM (
                  SELECT 'H' AS clase,id_historial AS identificador,fecha FROM historial_pedidos WHERE id_pedido=?
                  UNION ALL SELECT 'U',id,fecha_evento FROM eventos_ubicacion_pedido WHERE id_pedido=?
                  UNION ALL SELECT 'I',id_incidencia,fecha FROM incidencias_pedido WHERE id_pedido=?
                  UNION ALL SELECT 'C',id_checkpoint,fecha_evento FROM checkpoints_pedido
                    WHERE id_pedido=? AND estado_registro='APLICADO'
                ) movimientos ORDER BY fecha DESC,clase,identificador DESC LIMIT 1
                """, (r,n) -> new Movimiento(r.getString(1)+"-"+r.getLong(2),
                        instante(r.getObject(3,LocalDateTime.class))), pedido.getId(),pedido.getId(),
                pedido.getId(),pedido.getId());
        Movimiento ultimo = movimientos.isEmpty() || asignacion.fecha().isAfter(movimientos.get(0).fecha())
                ? asignacion : movimientos.get(0);
        return new CondicionAnomalia(asignacion.clave()+"-"+ultimo.clave(),oficinaResponsable(pedido.getId()),
                ultimo.fecha(),horas,null);
    }

    private CondicionAnomalia reintentos(Pedido pedido) {
        if (pedido.getIntentosEntregaFallidos() < 2) return null;

        return jdbc.query("""
                SELECT id_incidencia,fecha FROM incidencias_pedido WHERE id_pedido=? AND numero_intento=2
                ORDER BY id_incidencia DESC LIMIT 1
                """, (r,n) -> new CondicionAnomalia("INTENTO-"+r.getLong(1),oficinaResponsable(pedido.getId()),
                        r.getObject(2,LocalDateTime.class).toInstant(ZoneOffset.UTC),null,2), pedido.getId())
                .stream().findFirst().orElse(null);
    }

    private CondicionAnomalia recepcion(Pedido pedido, int horas) {
        Ubicacion llegada = ultimaUbicacion(pedido.getId());
        if (llegada == null || !llegada.etapa().equals("LLEGADO_OFICINA_DESTINO")) return null;
        Integer confirmada = jdbc.queryForObject("SELECT count(*) FROM recepciones_oficina WHERE evento_llegada_id=?",
                Integer.class,llegada.id());
        if (confirmada != null && confirmada > 0) return null;
        return new CondicionAnomalia("RECEPCION-"+llegada.id(),llegada.oficinaId(),instante(llegada.fecha()),horas,null);
    }

    private Instant instante(LocalDateTime fecha) { return fecha.atZone(zonaHistorica).toInstant(); }
    private record Movimiento(String clave, Instant fecha) {}
}

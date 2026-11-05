package com.udea.demo.pedidos.domain.event;

import com.udea.demo.compartido.eventos.EventoIntegracion;

import java.time.LocalDateTime;
import java.util.UUID;

public record DevolucionCreadaEvent(
        UUID eventId,
        String tipo,
        int version,
        LocalDateTime ocurridoEn,
        Long pedidoId,
        Long pedidoOrigenId,
        String numeroTracking,
        Long clienteId,
        String numeroTrackingOrigen,
        String correoDestinatario,
        Long conductorUsuarioId,
        String mensajeCliente
) implements EventoIntegracion {
    public static final String TIPO = "DEVOLUCION_CREADA";
    public static final String ROUTING_KEY = "pedido.devolucion.creada";

    public static DevolucionCreadaEvent de(Long pedidoId, String numeroTracking, Long clienteId,
                                           Long pedidoOrigenId,
                                           String numeroTrackingOrigen, String correoDestinatario,
                                           Long conductorUsuarioId, String mensajeCliente) {
        return new DevolucionCreadaEvent(UUID.randomUUID(), TIPO, 1, LocalDateTime.now(), pedidoId,
                pedidoOrigenId, numeroTracking, clienteId, numeroTrackingOrigen, correoDestinatario, conductorUsuarioId,
                mensajeCliente);
    }

    @Override public String routingKey() { return ROUTING_KEY; }
}

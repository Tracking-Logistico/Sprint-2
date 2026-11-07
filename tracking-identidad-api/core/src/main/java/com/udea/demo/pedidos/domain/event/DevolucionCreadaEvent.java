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

    @Override public String routingKey() { return ROUTING_KEY; }
}

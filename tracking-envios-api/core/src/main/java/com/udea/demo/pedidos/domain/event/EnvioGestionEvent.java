package com.udea.demo.pedidos.domain.event;

import com.udea.demo.compartido.eventos.EventoIntegracion;
import com.udea.demo.pedidos.domain.model.EstadoPedido;

import java.time.LocalDateTime;
import java.util.UUID;

public record EnvioGestionEvent(
        UUID eventId,
        String tipo,
        int version,
        LocalDateTime ocurridoEn,
        Long pedidoId,
        String numeroTracking,
        Long clienteId,
        EstadoPedido estado,
        String motivo,
        String correoDestinatario,
        Long conductorUsuarioId
) implements EventoIntegracion {
    public static final String TIPO = "ENVIO_GESTIONADO";
    public static final String ROUTING_KEY = "pedido.gestionado";

    public static EnvioGestionEvent de(Long pedidoId, String numeroTracking, Long clienteId, EstadoPedido estado,
                                       String motivo, String correoDestinatario, Long conductorUsuarioId) {
        return new EnvioGestionEvent(UUID.randomUUID(), TIPO, 1, LocalDateTime.now(), pedidoId, numeroTracking,
                clienteId, estado, motivo, correoDestinatario, conductorUsuarioId);
    }

    @Override public String routingKey() { return ROUTING_KEY; }
}

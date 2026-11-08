package com.udea.demo.pedidos.application.dto;

import com.udea.demo.pedidos.domain.model.EstadoPedido;
import java.time.LocalDateTime;

public record PedidoClienteResponseDTO(
        Long id,
        String numeroPedido,
        String numeroTracking,
        String remitenteNombre,
        String destinatarioNombre,
        EstadoPedido estado,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaEstimadaEntrega,
        String numeroTrackingOrigenDevolucion,
        String referenciaDevolucion,
        /** HU-13 CA-4: el remitente ve si su envío exige verificación de edad 18+. */
        Boolean requiereVerificacionEdad
) {
    /** Firma anterior a HU-13. */
    public PedidoClienteResponseDTO(Long id, String numeroPedido, String numeroTracking, String remitenteNombre,
                                    String destinatarioNombre, EstadoPedido estado, LocalDateTime fechaCreacion,
                                    LocalDateTime fechaEstimadaEntrega, String numeroTrackingOrigenDevolucion,
                                    String referenciaDevolucion) {
        this(id, numeroPedido, numeroTracking, remitenteNombre, destinatarioNombre, estado, fechaCreacion,
                fechaEstimadaEntrega, numeroTrackingOrigenDevolucion, referenciaDevolucion, false);
    }

    public PedidoClienteResponseDTO(Long id, String numeroPedido, String numeroTracking, String remitenteNombre,
                                    String destinatarioNombre, EstadoPedido estado, LocalDateTime fechaCreacion,
                                    LocalDateTime fechaEstimadaEntrega) {
        this(id, numeroPedido, numeroTracking, remitenteNombre, destinatarioNombre, estado, fechaCreacion,
                fechaEstimadaEntrega, null, null, false);
    }
}

package com.udea.demo.pedidos.application.dto;

import com.udea.demo.pedidos.domain.model.*;
import java.time.LocalDateTime;

public record PedidoResponseDTO(
    Long id,
    String numeroPedido,
    Long clienteId,
    String direccionOrigen,
    String direccionDestino,
    String descripcionPaquete,
    Double pesoKg,
    Double largoCm,
    Double anchoCm,
    Double altoCm,
    TipoServicio tipoServicio,
    Prioridad prioridadSugerida,
    Prioridad prioridadConfirmada,
    EstadoPedido estado,
    String observacionesValidacion,
    Long operadorValidadorId,
    LocalDateTime fechaCreacion,
    LocalDateTime fechaValidacion,
    String numeroTracking,
    LocalDateTime fechaActivacionTracking,
    Boolean etiquetaImpresa,
    LocalDateTime fechaImpresionEtiqueta,
    String destinatarioNombre,
    String destinatarioTelefono,
    String justificacionPrioridad,
    String ciudadOrigen,
    String ciudadDestino,
    String codigoPostalOrigen,
    String codigoPostalDestino,
    String remitenteNombre,
    String remitenteEmail,
    String remitenteTelefono,
    LocalDateTime fechaEstimadaEntrega,
    String indicacionesAcceso,                        // ← nuevo
    java.time.LocalDate fechaEntregaReprogramada,
    String numeroTrackingOrigenDevolucion,
    String referenciaDevolucion,
    /** HU-13 CA-4/CA-5: el envío exige verificación de edad 18+ en la entrega. */
    Boolean requiereVerificacionEdad
) {
    /** Firma anterior a HU-13. */
    public PedidoResponseDTO(Long id, String numeroPedido, Long clienteId, String direccionOrigen,
            String direccionDestino, String descripcionPaquete, Double pesoKg, Double largoCm, Double anchoCm,
            Double altoCm, TipoServicio tipoServicio, Prioridad prioridadSugerida, Prioridad prioridadConfirmada,
            EstadoPedido estado, String observacionesValidacion, Long operadorValidadorId, LocalDateTime fechaCreacion,
            LocalDateTime fechaValidacion, String numeroTracking, LocalDateTime fechaActivacionTracking,
            Boolean etiquetaImpresa, LocalDateTime fechaImpresionEtiqueta, String destinatarioNombre,
            String destinatarioTelefono, String justificacionPrioridad, String ciudadOrigen, String ciudadDestino,
            String codigoPostalOrigen, String codigoPostalDestino, String remitenteNombre, String remitenteEmail,
            String remitenteTelefono, LocalDateTime fechaEstimadaEntrega, String indicacionesAcceso,
            java.time.LocalDate fechaEntregaReprogramada, String numeroTrackingOrigenDevolucion,
            String referenciaDevolucion) {
        this(id, numeroPedido, clienteId, direccionOrigen, direccionDestino, descripcionPaquete, pesoKg, largoCm,
                anchoCm, altoCm, tipoServicio, prioridadSugerida, prioridadConfirmada, estado, observacionesValidacion,
                operadorValidadorId, fechaCreacion, fechaValidacion, numeroTracking, fechaActivacionTracking,
                etiquetaImpresa, fechaImpresionEtiqueta, destinatarioNombre, destinatarioTelefono,
                justificacionPrioridad, ciudadOrigen, ciudadDestino, codigoPostalOrigen, codigoPostalDestino,
                remitenteNombre, remitenteEmail, remitenteTelefono, fechaEstimadaEntrega, indicacionesAcceso,
                fechaEntregaReprogramada, numeroTrackingOrigenDevolucion, referenciaDevolucion, false);
    }

    public PedidoResponseDTO(Long id, String numeroPedido, Long clienteId, String direccionOrigen,
            String direccionDestino, String descripcionPaquete, Double pesoKg, Double largoCm, Double anchoCm,
            Double altoCm, TipoServicio tipoServicio, Prioridad prioridadSugerida, Prioridad prioridadConfirmada,
            EstadoPedido estado, String observacionesValidacion, Long operadorValidadorId, LocalDateTime fechaCreacion,
            LocalDateTime fechaValidacion, String numeroTracking, LocalDateTime fechaActivacionTracking,
            Boolean etiquetaImpresa, LocalDateTime fechaImpresionEtiqueta, String destinatarioNombre,
            String destinatarioTelefono, String justificacionPrioridad, String ciudadOrigen, String ciudadDestino,
            String codigoPostalOrigen, String codigoPostalDestino, String remitenteNombre, String remitenteEmail,
            String remitenteTelefono, LocalDateTime fechaEstimadaEntrega, String indicacionesAcceso,
            java.time.LocalDate fechaEntregaReprogramada) {
        this(id, numeroPedido, clienteId, direccionOrigen, direccionDestino, descripcionPaquete, pesoKg, largoCm,
                anchoCm, altoCm, tipoServicio, prioridadSugerida, prioridadConfirmada, estado, observacionesValidacion,
                operadorValidadorId, fechaCreacion, fechaValidacion, numeroTracking, fechaActivacionTracking,
                etiquetaImpresa, fechaImpresionEtiqueta, destinatarioNombre, destinatarioTelefono, justificacionPrioridad,
                ciudadOrigen, ciudadDestino, codigoPostalOrigen, codigoPostalDestino, remitenteNombre, remitenteEmail,
                remitenteTelefono, fechaEstimadaEntrega, indicacionesAcceso, fechaEntregaReprogramada, null, null);
    }

    public PedidoResponseDTO(Long id, String numeroPedido, Long clienteId, String direccionOrigen,
            String direccionDestino, String descripcionPaquete, Double pesoKg, Double largoCm, Double anchoCm,
            Double altoCm, TipoServicio tipoServicio, Prioridad prioridadSugerida, Prioridad prioridadConfirmada,
            EstadoPedido estado, String observacionesValidacion, Long operadorValidadorId, LocalDateTime fechaCreacion,
            LocalDateTime fechaValidacion, String numeroTracking, LocalDateTime fechaActivacionTracking,
            Boolean etiquetaImpresa, LocalDateTime fechaImpresionEtiqueta) {
        this(id, numeroPedido, clienteId, direccionOrigen, direccionDestino, descripcionPaquete, pesoKg, largoCm, anchoCm,
                altoCm, tipoServicio, prioridadSugerida, prioridadConfirmada, estado, observacionesValidacion,
                operadorValidadorId, fechaCreacion, fechaValidacion, numeroTracking, fechaActivacionTracking,
                etiquetaImpresa, fechaImpresionEtiqueta, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null);
    }
}

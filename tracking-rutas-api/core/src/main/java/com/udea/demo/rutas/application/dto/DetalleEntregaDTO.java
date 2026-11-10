package com.udea.demo.rutas.application.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DetalleEntregaDTO(
        Long idParada,
        Long pedidoId,
        Integer orden,
        String numeroPedido,
        String numeroTracking,
        String direccionDestino,
        String ciudadDestino,
        String codigoPostalDestino,
        String destinatarioNombre,
        String destinatarioTelefono,
        String indicacionesAcceso,
        String descripcionPaquete,
        Double pesoKg,
        Double largoCm,
        Double anchoCm,
        Double altoCm,
        String prioridad,
        String estadoParada,
        String estadoPedido,
        String observacionesValidacion,
        LocalDate fechaEntregaReprogramada,
        List<EventoHistorialDTO> ultimosEventos,
        /** HU-13 CA-5: indicador "Requiere verificación de edad (18+)". */
        Boolean requiereVerificacionEdad,
        /** HU-13: resultado ya registrado (CUMPLE o NO_CUMPLE), o null si falta. Habilita o no los botones. */
        String resultadoVerificacionEdad
) {
    /** Firma anterior a HU-13. */
    public DetalleEntregaDTO(Long idParada, Long pedidoId, Integer orden, String numeroPedido, String numeroTracking,
                             String direccionDestino, String ciudadDestino, String codigoPostalDestino,
                             String destinatarioNombre, String destinatarioTelefono, String indicacionesAcceso,
                             String descripcionPaquete, Double pesoKg, Double largoCm, Double anchoCm, Double altoCm,
                             String prioridad, String estadoParada, String estadoPedido, String observacionesValidacion,
                             LocalDate fechaEntregaReprogramada, List<EventoHistorialDTO> ultimosEventos) {
        this(idParada, pedidoId, orden, numeroPedido, numeroTracking, direccionDestino, ciudadDestino,
                codigoPostalDestino, destinatarioNombre, destinatarioTelefono, indicacionesAcceso, descripcionPaquete,
                pesoKg, largoCm, anchoCm, altoCm, prioridad, estadoParada, estadoPedido, observacionesValidacion,
                fechaEntregaReprogramada, ultimosEventos, false, null);
    }

    public record EventoHistorialDTO(String tipoEvento, String detalle, LocalDateTime fecha) {}
}
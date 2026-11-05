package com.udea.demo.pedidos.application.dto;

import com.udea.demo.pedidos.domain.model.EstadoPedido;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SeguimientoClienteResponseDTO(
        Long id,
        String numeroPedido,
        String numeroTracking,
        EstadoPedido estado,
        LocalDateTime fechaEstimadaEntrega,
        List<MovimientoSeguimientoResponseDTO> movimientos,
        String descripcionEstado,
        NovedadClienteDTO novedad,
        LocalDate fechaEntregaReprogramada,
        LocalDateTime fechaLimiteVerificacionDireccion,
        String numeroTrackingOrigenDevolucion,
        String referenciaDevolucion,
        List<UbicacionResponseDTO> ubicaciones
) {
    public SeguimientoClienteResponseDTO(Long id, String numeroPedido, String numeroTracking, EstadoPedido estado,
                                         LocalDateTime fechaEstimadaEntrega,
                                         List<MovimientoSeguimientoResponseDTO> movimientos, String descripcionEstado,
                                         NovedadClienteDTO novedad, LocalDate fechaEntregaReprogramada,
                                         LocalDateTime fechaLimiteVerificacionDireccion) {
        this(id, numeroPedido, numeroTracking, estado, fechaEstimadaEntrega, movimientos, descripcionEstado, novedad,
                fechaEntregaReprogramada, fechaLimiteVerificacionDireccion, null, null, List.of());
    }

    public SeguimientoClienteResponseDTO(Long id, String numeroPedido, String numeroTracking, EstadoPedido estado,
                                         LocalDateTime fechaEstimadaEntrega,
                                         List<MovimientoSeguimientoResponseDTO> movimientos, String descripcionEstado,
                                         NovedadClienteDTO novedad, LocalDate fechaEntregaReprogramada,
                                         LocalDateTime fechaLimiteVerificacionDireccion,
                                         String numeroTrackingOrigenDevolucion, String referenciaDevolucion) {
        this(id, numeroPedido, numeroTracking, estado, fechaEstimadaEntrega, movimientos, descripcionEstado, novedad,
                fechaEntregaReprogramada, fechaLimiteVerificacionDireccion, numeroTrackingOrigenDevolucion,
                referenciaDevolucion, List.of());
    }

    public SeguimientoClienteResponseDTO conUbicaciones(List<UbicacionResponseDTO> nuevasUbicaciones) {
        return new SeguimientoClienteResponseDTO(id, numeroPedido, numeroTracking, estado, fechaEstimadaEntrega,
                movimientos, descripcionEstado, novedad, fechaEntregaReprogramada,
                fechaLimiteVerificacionDireccion, numeroTrackingOrigenDevolucion, referenciaDevolucion,
                nuevasUbicaciones);
    }
}

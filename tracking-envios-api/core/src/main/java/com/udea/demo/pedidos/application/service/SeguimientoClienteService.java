package com.udea.demo.pedidos.application.service;

import com.udea.demo.pedidos.application.dto.MovimientoSeguimientoResponseDTO;
import com.udea.demo.pedidos.application.dto.NovedadClienteDTO;
import com.udea.demo.pedidos.application.dto.SeguimientoClienteResponseDTO;
import com.udea.demo.pedidos.domain.model.EstadoPedido;
import com.udea.demo.pedidos.domain.model.HistorialPedido;
import com.udea.demo.pedidos.domain.model.Pedido;
import com.udea.demo.pedidos.domain.model.TipoIncidencia;
import com.udea.demo.pedidos.interfaces.persistence.HistorialPedidoRepository;
import com.udea.demo.pedidos.interfaces.persistence.IncidenciaPedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Seguimiento de la cuenta del cliente: estado, movimientos y novedades en lenguaje para el cliente. */
@Service
public class SeguimientoClienteService {
    private static final String TRACKING_ACTIVADO = "TRACKING_ACTIVADO";

    private final AccesoClientePedido acceso;
    private final HistorialPedidoRepository historial;
    private final IncidenciaPedidoRepository incidencias;

    public SeguimientoClienteService(AccesoClientePedido acceso, HistorialPedidoRepository historial,
                                     IncidenciaPedidoRepository incidencias) {
        this.acceso = acceso;
        this.historial = historial;
        this.incidencias = incidencias;
    }

    @Transactional(readOnly = true)
    public SeguimientoClienteResponseDTO obtener(String numeroTracking) {
        return construir(acceso.pedidoDelClienteActual(numeroTracking));
    }

    public SeguimientoClienteResponseDTO construir(Pedido p) {
        var movimientos = historial.findByPedidoIdOrderByFechaAsc(p.getId()).stream()
                .map(this::movimiento).flatMap(Optional::stream).toList();
        NovedadClienteDTO novedad = incidencias.findFirstByPedidoIdOrderByFechaDescIdDesc(p.getId())
                .map(i -> new NovedadClienteDTO(i.getTipo().descripcion(), i.getTipo().mensajeCliente(), i.getFecha()))
                .orElse(null);
        return new SeguimientoClienteResponseDTO(p.getId(), p.getNumeroPedido(), p.getNumeroTracking(),
                p.getEstado(), p.getFechaEstimadaEntrega(), movimientos, MensajesSeguimientoCliente.estado(p.getEstado()),
                novedad, p.getFechaEntregaReprogramada(), p.getFechaLimiteVerificacionDireccion(),
                p.getNumeroTrackingOrigenDevolucion(), p.getNumeroTrackingOrigenDevolucion() == null ? null
                        : "Devolución del envío " + p.getNumeroTrackingOrigenDevolucion());
    }

    private Optional<MovimientoSeguimientoResponseDTO> movimiento(HistorialPedido h) {
        return switch (h.getTipoEvento()) {
            case TRACKING_ACTIVADO -> Optional.of(estado(EstadoPedido.CREADO, h));
            case RegistroHistorialPedido.ESTADO_LOGISTICO -> Optional.of(estado(EstadoPedido.valueOf(h.getDetalle()), h));
            case RegistroHistorialPedido.CHECKPOINT -> Optional.of(new MovimientoSeguimientoResponseDTO(
                    null, h.getFecha(), "PUNTO_CONTROL", MensajesSeguimientoCliente.PUNTO_CONTROL));
            case RegistroHistorialPedido.INCIDENCIA -> novedad(h);
            default -> Optional.empty();
        };
    }

    private static MovimientoSeguimientoResponseDTO estado(EstadoPedido estado, HistorialPedido h) {
        return new MovimientoSeguimientoResponseDTO(estado, h.getFecha(), "ESTADO", MensajesSeguimientoCliente.estado(estado));
    }

    private static Optional<MovimientoSeguimientoResponseDTO> novedad(HistorialPedido h) {
        try {
            var tipo = TipoIncidencia.valueOf(h.getDetalle());
            return Optional.of(new MovimientoSeguimientoResponseDTO(null, h.getFecha(), "NOVEDAD", tipo.mensajeCliente()));
        } catch (IllegalArgumentException | NullPointerException ex) {
            return Optional.empty();
        }
    }
}

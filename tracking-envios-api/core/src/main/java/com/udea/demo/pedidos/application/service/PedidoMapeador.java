package com.udea.demo.pedidos.application.service;

import com.udea.demo.pedidos.application.dto.*;
import com.udea.demo.pedidos.domain.model.*;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class PedidoMapeador {
    public PedidoResponseDTO map(Pedido p) {
        return new PedidoResponseDTO(p.getId(), p.getNumeroPedido(), p.getClienteId(), p.getDireccionOrigen(),
                p.getDireccionDestino(), p.getDescripcionPaquete(), p.getPesoKg(), p.getLargoCm(), p.getAnchoCm(),
                p.getAltoCm(), p.getTipoServicio(), p.getPrioridadSugerida(), p.getPrioridadConfirmada(), p.getEstado(),
                p.getObservacionesValidacion(), p.getOperadorValidadorId(), p.getFechaCreacion(), p.getFechaValidacion(),
                p.getNumeroTracking(), p.getFechaActivacionTracking(), p.getEtiquetaImpresa(), p.getFechaImpresionEtiqueta(),
                p.getDestinatarioNombre(), p.getDestinatarioTelefono(), p.getJustificacionPrioridad(),
                p.getCiudadOrigen(), p.getCiudadDestino(), p.getCodigoPostalOrigen(), p.getCodigoPostalDestino(),
                p.getRemitenteNombre(), p.getRemitenteEmail(), p.getRemitenteTelefono(), p.getFechaEstimadaEntrega(),
                p.getIndicacionesAcceso(), p.getFechaEntregaReprogramada(), p.getNumeroTrackingOrigenDevolucion(),
                referenciaDevolucion(p));
    }

    public PedidoClienteResponseDTO mapCliente(Pedido p) {
        LocalDateTime fechaEstimada = p.getFechaEstimadaEntrega();
        if (fechaEstimada == null && p.getFechaCreacion() != null) {
            fechaEstimada = p.getFechaCreacion().plusDays(p.getTipoServicio() == TipoServicio.EXPRESS ? 1 : 3);
        }
        return new PedidoClienteResponseDTO(p.getId(), p.getNumeroPedido(), p.getNumeroTracking(),
                p.getRemitenteNombre(), p.getDestinatarioNombre(), p.getEstado(),
                p.getFechaCreacion(), fechaEstimada, p.getNumeroTrackingOrigenDevolucion(),
                referenciaDevolucion(p));
    }

    private static String referenciaDevolucion(Pedido p) {
        return p.getNumeroTrackingOrigenDevolucion() == null ? null
                : "Devolución del envío " + p.getNumeroTrackingOrigenDevolucion();
    }
}

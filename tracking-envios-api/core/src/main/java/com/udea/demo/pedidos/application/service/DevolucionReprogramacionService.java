package com.udea.demo.pedidos.application.service;

import com.udea.demo.pedidos.domain.event.DevolucionIniciadaEvent;
import com.udea.demo.pedidos.domain.model.EstadoPedido;
import com.udea.demo.pedidos.domain.model.MotivoDevolucion;
import com.udea.demo.pedidos.domain.model.Pedido;
import com.udea.demo.pedidos.interfaces.persistence.PedidoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DevolucionReprogramacionService {
    private final PedidoRepository pedidos;
    private final RegistroHistorialPedido historial;
    private final ApplicationEventPublisher eventos;
    private final int diasRetencionBodega;
    private final int maxIntentosEntrega;

    public DevolucionReprogramacionService(PedidoRepository pedidos, RegistroHistorialPedido historial,
                                           ApplicationEventPublisher eventos,
                                           @Value("${app.shipment.retencion-bodega-dias:7}") int diasRetencionBodega,
                                           @Value("${app.incidencias.max-intentos-entrega:3}") int maxIntentosEntrega) {
        this.pedidos = pedidos;
        this.historial = historial;
        this.eventos = eventos;
        this.diasRetencionBodega = diasRetencionBodega;
        this.maxIntentosEntrega = maxIntentosEntrega;
    }

    @Transactional(readOnly = true)
    public List<Long> pendientesDeEscalar(LocalDateTime ahora) {
        return pedidos.findIdsConPlazoReprogramacionVencido(EstadoPedido.ENTREGA_FALLIDA,
                Math.max(1, maxIntentosEntrega - 1),
                ahora.minusDays(diasRetencionBodega));
    }

    @Transactional
    public boolean escalarVencida(Long pedidoId, LocalDateTime ahora) {
        Pedido pedido = pedidos.findByIdForUpdate(pedidoId).orElse(null);
        if (pedido == null) return false;
        EstadoPedido anterior = pedido.getEstado();
        if (!pedido.devolverPorPlazoReprogramacionVencido(ahora, diasRetencionBodega,
                maxIntentosEntrega)) return false;
        pedidos.saveAndFlush(pedido);
        historial.registrar(pedidoId, null, RegistroHistorialPedido.ESTADO_LOGISTICO,
                RegistroHistorialPedido.INCIDENCIA, pedido.getEstado().name(), ahora);
        eventos.publishEvent(DevolucionIniciadaEvent.de(pedido, anterior,
                MotivoDevolucion.PLAZO_REPROGRAMACION_VENCIDO));
        return true;
    }
}

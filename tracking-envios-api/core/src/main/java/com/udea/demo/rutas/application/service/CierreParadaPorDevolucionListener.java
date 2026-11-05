package com.udea.demo.rutas.application.service;

import com.udea.demo.pedidos.domain.event.DevolucionIniciadaEvent;
import com.udea.demo.pedidos.domain.event.EnvioGestionEvent;
import com.udea.demo.pedidos.domain.model.EstadoPedido;
import com.udea.demo.rutas.domain.model.EstadoParada;
import com.udea.demo.rutas.domain.model.Ruta;
import com.udea.demo.rutas.interfaces.persistence.ParadaRutaRepository;
import com.udea.demo.rutas.interfaces.persistence.RutaRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Un envío en devolución deja de ser una entrega pendiente del conductor. Se ejecuta en la misma
 * transacción que origina la devolución para mantener la ruta consistente con el estado del envío.
 */
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.api.role", havingValue = "rutas")
@Component
public class CierreParadaPorDevolucionListener {
    private final ParadaRutaRepository paradas;
    private final RutaRepository rutas;

    public CierreParadaPorDevolucionListener(ParadaRutaRepository paradas, RutaRepository rutas) {
        this.paradas = paradas;
        this.rutas = rutas;
    }

    @EventListener
    public void alIniciarDevolucion(DevolucionIniciadaEvent evento) {
        paradas.findByPedidoIdAndEstado(evento.pedidoId(), EstadoParada.PENDIENTE).ifPresent(parada -> {
            Ruta ruta = parada.getRuta();
            ruta.cancelarParada(evento.pedidoId());
            rutas.save(ruta);
        });
    }

    @EventListener
    public void alPausarOCancelar(EnvioGestionEvent evento) {
        if (evento.estado() != EstadoPedido.PAUSADO && evento.estado() != EstadoPedido.CANCELADO) return;
        paradas.findByPedidoIdAndEstado(evento.pedidoId(), EstadoParada.PENDIENTE).ifPresent(parada -> {
            Ruta ruta = parada.getRuta();
            if (evento.estado() == EstadoPedido.CANCELADO) {
                ruta.cancelarParada(evento.pedidoId());
            }
            rutas.save(ruta);
        });
    }
}

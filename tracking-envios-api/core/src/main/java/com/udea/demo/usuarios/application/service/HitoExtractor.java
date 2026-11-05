package com.udea.demo.usuarios.application.service;

import com.udea.demo.compartido.eventos.EventoIntegracion;
import com.udea.demo.pedidos.domain.event.CheckpointRegistradoEvent;
import com.udea.demo.pedidos.domain.event.DevolucionCreadaEvent;
import com.udea.demo.pedidos.domain.event.EntregaConfirmadaEvent;
import com.udea.demo.pedidos.domain.event.EntregaReprogramadaEvent;
import com.udea.demo.pedidos.domain.event.EnvioGestionEvent;
import com.udea.demo.pedidos.domain.event.IncidenciaRegistradaEvent;
import com.udea.demo.pedidos.domain.model.EstadoPedido;
import com.udea.demo.pedidos.domain.model.EtapaCheckpoint;
import com.udea.demo.usuarios.domain.model.HitoNotificacion;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * SRP: única responsabilidad es decidir si un evento es un hito notificable
 * y, si lo es, convertirlo en un HitoNotificacion.
 *
 * OCP: añadir un hito nuevo = añadir un bloque if sin tocar el resto.
 */
@Component
public class HitoExtractor {

    public Optional<HitoNotificacion> extraer(EventoIntegracion evento) {
        if (evento instanceof EnvioGestionEvent e) {
            return Optional.of(new HitoNotificacion(e.pedidoId(), e.numeroTracking(), e.clienteId(),
                    e.estado(), e.motivo(), e.tipo()));
        }
        if (evento instanceof DevolucionCreadaEvent e) {
            return Optional.of(new HitoNotificacion(e.pedidoId(), e.numeroTracking(), e.clienteId(),
                    EstadoPedido.CREADO, e.mensajeCliente(), e.tipo()));
        }
        if (evento instanceof EntregaConfirmadaEvent e) {
            return Optional.of(new HitoNotificacion(
                    e.pedidoId(), e.numeroTracking(), e.clienteId(),
                    EstadoPedido.ENTREGADO, null, "ENTREGA_CONFIRMADA"));
        }
        if (evento instanceof CheckpointRegistradoEvent e) {
            // RECIBIDO_EN_ORIGEN no es un hito para el cliente.
            if (e.etapa() == EtapaCheckpoint.RECIBIDO_EN_ORIGEN) return Optional.empty();
            return Optional.of(new HitoNotificacion(
                    e.pedidoId(), e.numeroTracking(), e.clienteId(),
                    e.estadoActual(), null, "CHECKPOINT_REGISTRADO"));
        }
        if (evento instanceof IncidenciaRegistradaEvent e) {
            if (e.estadoActual() != EstadoPedido.ENTREGA_FALLIDA) return Optional.empty();
            return Optional.of(new HitoNotificacion(
                    e.pedidoId(), e.numeroTracking(), e.clienteId(),
                    e.estadoActual(), e.mensajeCliente(), "INCIDENCIA_REGISTRADA"));
        }
        if (evento instanceof EntregaReprogramadaEvent e) {
            return Optional.of(new HitoNotificacion(
                    e.pedidoId(), e.numeroTracking(), e.clienteId(),
                    EstadoPedido.ENTREGA_REPROGRAMADA, e.mensajeCliente(), "ENTREGA_REPROGRAMADA"));
        }
        return Optional.empty();
    }
}
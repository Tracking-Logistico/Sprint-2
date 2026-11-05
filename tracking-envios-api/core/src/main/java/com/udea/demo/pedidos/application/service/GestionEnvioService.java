package com.udea.demo.pedidos.application.service;

import com.udea.demo.pedidos.application.dto.GestionEnvioRequestDTO;
import com.udea.demo.pedidos.application.dto.GestionEnvioResponseDTO;
import com.udea.demo.pedidos.domain.event.DevolucionIniciadaEvent;
import com.udea.demo.pedidos.domain.event.EnvioGestionEvent;
import com.udea.demo.pedidos.domain.exception.PedidoNoEncontradoException;
import com.udea.demo.pedidos.domain.model.EstadoPedido;
import com.udea.demo.pedidos.domain.model.MotivoDevolucion;
import com.udea.demo.pedidos.domain.model.Pedido;
import com.udea.demo.pedidos.application.service.RegistroHistorialPedido;
import com.udea.demo.pedidos.interfaces.persistence.PedidoRepository;
import com.udea.demo.rutas.domain.model.EstadoParada;
import com.udea.demo.rutas.interfaces.persistence.ParadaRutaRepository;
import com.udea.demo.usuarios.application.service.ActorAuthorizationService;
import com.udea.demo.usuarios.domain.model.Rol;
import com.udea.demo.usuarios.domain.model.Usuario;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class GestionEnvioService {
    private static final String CAMPO_MOTIVO = "MOTIVO";

    private final PedidoRepository pedidos;
    private final ActorAuthorizationService actores;
    private final RegistroHistorialPedido historial;
    private final ParadaRutaRepository paradas;
    private final ApplicationEventPublisher eventos;

    public GestionEnvioService(PedidoRepository pedidos, ActorAuthorizationService actores,
                               RegistroHistorialPedido historial, ParadaRutaRepository paradas,
                               ApplicationEventPublisher eventos) {
        this.pedidos = pedidos;
        this.actores = actores;
        this.historial = historial;
        this.paradas = paradas;
        this.eventos = eventos;
    }

    @Transactional
    public GestionEnvioResponseDTO pausar(Long pedidoId, GestionEnvioRequestDTO solicitud) {
        Usuario actor = actores.actorActual();
        Pedido pedido = cargarYAutorizar(pedidoId, actor);
        pedido.pausar();
        LocalDateTime ahora = LocalDateTime.now();
        pedidos.saveAndFlush(pedido);
        historial.registrar(pedidoId, actor.getId(), "ENVIO_PAUSADO", CAMPO_MOTIVO,
                solicitud.motivo().trim(), ahora);
        historial.registrar(pedidoId, actor.getId(), RegistroHistorialPedido.ESTADO_LOGISTICO,
                null, EstadoPedido.PAUSADO.name(), ahora);
        notificarPartes(pedido, EstadoPedido.PAUSADO,
                "El envío fue pausado. Motivo: " + solicitud.motivo().trim());
        return respuesta(pedido, null);
    }

    @Transactional
    public GestionEnvioResponseDTO reanudar(Long pedidoId) {
        Usuario actor = actores.actorActual();
        Pedido pedido = cargarYAutorizar(pedidoId, actor);
        pedido.reanudar();
        LocalDateTime ahora = LocalDateTime.now();
        pedidos.saveAndFlush(pedido);
        historial.registrar(pedidoId, actor.getId(), "ENVIO_REANUDADO", null,
                pedido.getEstado().name(), ahora);
        historial.registrar(pedidoId, actor.getId(), RegistroHistorialPedido.ESTADO_LOGISTICO,
                null, pedido.getEstado().name(), ahora);
        notificarPartes(pedido, pedido.getEstado(), "El envío fue reanudado");
        return respuesta(pedido, null);
    }

    @Transactional
    public GestionEnvioResponseDTO cancelar(Long pedidoId, GestionEnvioRequestDTO solicitud) {
        Usuario actor = actores.actorActual();
        Pedido pedido = cargarYAutorizar(pedidoId, actor);
        if (pedido.getEstado() == EstadoPedido.CANCELADO) {
            Pedido devolucion = pedidos.findByPedidoOrigenDevolucionId(pedidoId)
                    .orElseThrow(() -> new IllegalStateException(
                            "El envío cancelado no tiene su devolución trazable asociada"));
            return respuesta(pedido, devolucion.getNumeroTracking());
        }

        EstadoPedido anterior = pedido.getEstado();
        pedido.cancelar();
        LocalDateTime ahora = LocalDateTime.now();
        pedidos.saveAndFlush(pedido);
        historial.registrar(pedidoId, actor.getId(), "ENVIO_CANCELADO", CAMPO_MOTIVO,
                solicitud.motivo().trim(), ahora);
        historial.registrar(pedidoId, actor.getId(), RegistroHistorialPedido.ESTADO_LOGISTICO,
                null, EstadoPedido.CANCELADO.name(), ahora);

        eventos.publishEvent(DevolucionIniciadaEvent.de(pedido, anterior, MotivoDevolucion.CANCELACION));
        Pedido devolucion = pedidos.findByPedidoOrigenDevolucionId(pedidoId)
                .orElseThrow(() -> new IllegalStateException(
                        "No se creó el envío de devolución del pedido cancelado"));
        return respuesta(pedido, devolucion.getNumeroTracking());
    }

    private Pedido cargarYAutorizar(Long pedidoId, Usuario actor) {
        Pedido pedido = pedidos.findByIdForUpdate(pedidoId)
                .orElseThrow(() -> new PedidoNoEncontradoException(pedidoId));
        autorizar(pedido, actor);
        return pedido;
    }

    private void autorizar(Pedido pedido, Usuario actor) {
        if (actor.getRol() == Rol.OPERADOR) {
            actores.operadorActualId();
            return;
        }
        if (actor.getRol() != Rol.CLIENTE) {
            throw new AccessDeniedException("El rol de la sesión no permite pausar o cancelar envíos");
        }
        boolean remitente = pedido.getClienteId().equals(actores.clienteActualId());
        boolean destinatario = pedido.getDestinatarioEmail() != null
                && pedido.getDestinatarioEmail().equalsIgnoreCase(actor.getEmail());
        if (!remitente && !destinatario) {
            throw new AccessDeniedException("El cliente autenticado no es parte de este envío");
        }
    }

    private void notificarPartes(Pedido pedido, EstadoPedido estado, String mensaje) {
        Long conductor = paradas.findConductorUsuarioIdByPedidoIdAndEstado(
                pedido.getId(), EstadoParada.PENDIENTE).orElse(null);
        eventos.publishEvent(EnvioGestionEvent.de(pedido.getId(), pedido.getNumeroTracking(),
                pedido.getClienteId(), estado, mensaje, pedido.getDestinatarioEmail(), conductor));
    }

    private static GestionEnvioResponseDTO respuesta(Pedido pedido, String numeroTrackingDevolucion) {
        return new GestionEnvioResponseDTO(pedido.getId(), pedido.getNumeroTracking(),
                pedido.getEstado(), numeroTrackingDevolucion);
    }
}

package com.udea.demo.usuarios.application.service;

import com.udea.demo.compartido.eventos.EventoIntegracion;
import com.udea.demo.pedidos.domain.model.EstadoPedido;
import com.udea.demo.usuarios.domain.model.*;
import com.udea.demo.usuarios.infrastructure.rabbit.NotificacionRabbitConfig;
import com.udea.demo.usuarios.interfaces.persistence.ClienteRepository;
import com.udea.demo.usuarios.interfaces.persistence.NotificacionEnviadaRepository;
import com.udea.demo.usuarios.interfaces.persistence.PreferenciaNotificacionRepository;
import com.udea.demo.usuarios.interfaces.services.EmailServiceI;
import com.udea.demo.usuarios.interfaces.services.NotificacionServiceI;
import com.udea.demo.usuarios.interfaces.services.SmsSenderI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * SRP: orquesta el envío. No extrae hitos (eso es HitoExtractor), no construye
 * mensajes (PlantillaMensaje), no envía por canal (EmailService/SmsSender).
 *
 * DIP: depende de EmailServiceI y SmsSenderI, no de implementaciones concretas.
 */
@Service
public class NotificacionService implements NotificacionServiceI {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private final HitoExtractor hitoExtractor;
    private final ClienteRepository clienteRepository;
    private final PreferenciaNotificacionRepository preferenciaRepository;
    private final NotificacionEnviadaRepository notificacionRepository;
    private final EmailServiceI emailService;
    private final SmsSenderI smsSender;
    private final PlantillaMensaje plantilla;
    private final RabbitTemplate rabbitTemplate;
    private final int maxIntentos;

    public NotificacionService(HitoExtractor hitoExtractor,
                               ClienteRepository clienteRepository,
                               PreferenciaNotificacionRepository preferenciaRepository,
                               NotificacionEnviadaRepository notificacionRepository,
                               EmailServiceI emailService,
                               SmsSenderI smsSender,
                               PlantillaMensaje plantilla,
                               RabbitTemplate rabbitTemplate,
                               @Value("${app.notifications.max-intentos:3}") int maxIntentos) {
        this.hitoExtractor = hitoExtractor;
        this.clienteRepository = clienteRepository;
        this.preferenciaRepository = preferenciaRepository;
        this.notificacionRepository = notificacionRepository;
        this.emailService = emailService;
        this.smsSender = smsSender;
        this.plantilla = plantilla;
        this.rabbitTemplate = rabbitTemplate;
        this.maxIntentos = maxIntentos;
    }

    @Override
    public void procesar(EventoIntegracion evento) {
        Optional<HitoNotificacion> opt = hitoExtractor.extraer(evento);
        if (opt.isEmpty()) {
            log.debug("Evento no notificable tipo={} eventId={}", evento.tipo(), evento.eventId());
            return;
        }
        HitoNotificacion hito = opt.get();

        ClienteSnapshot cliente = resolverCliente(hito.clienteId());
        if (cliente == null) {
            log.warn("Cliente {} no encontrado. eventId={}", hito.clienteId(), evento.eventId());
            return;
        }

        PreferenciaNotificacion pref = preferenciaRepository.findById(cliente.id())
                .orElseGet(() -> new PreferenciaNotificacion(cliente.id()));

        PlantillaMensaje.Mensaje msg = construirMensaje(hito);

        if (pref.prefiereEmail()) {
            enviarEmail(evento, cliente, hito, msg);
        }
        if (pref.prefiereSms()) {
            enviarSms(evento, cliente, pref, hito, msg);
        }
    }

    // ---------- Email ----------

    private void enviarEmail(EventoIntegracion evento, ClienteSnapshot cliente,
                             HitoNotificacion hito, PlantillaMensaje.Mensaje msg) {
        String eventId = evento.eventId().toString();
        Optional<NotificacionEnviada> previa = notificacionRepository
                .findByEventIdAndCanal(eventId, CanalNotificacion.EMAIL);
        if (previa.isPresent() && previa.get().getEstado() == EstadoNotificacion.ENVIADA) {
            log.debug("Email ya enviado eventId={}", eventId);
            return;
        }

        NotificacionEnviada notif = previa.orElseGet(() -> persistirPendiente(
                eventId, hito, cliente, CanalNotificacion.EMAIL, cliente.email(), msg));

        try {
            emailService.enviarNotificacionCambioEstado(cliente.email(), msg.asunto(), msg.cuerpo());
            marcarEnviada(notif);
        } catch (RuntimeException ex) {
            manejarFallo(notif, eventId, "EMAIL", ex);
        }
    }

    // ---------- SMS ----------

    private void enviarSms(EventoIntegracion evento, ClienteSnapshot cliente,
                           PreferenciaNotificacion pref, HitoNotificacion hito,
                           PlantillaMensaje.Mensaje msg) {
        String telefono = pref.getTelefonoSms() != null && !pref.getTelefonoSms().isBlank()
                ? pref.getTelefonoSms() : cliente.telefono();
        if (telefono == null || telefono.isBlank()) {
            log.warn("Cliente {} prefirió SMS pero no tiene teléfono. Se omite.", cliente.id());
            return;
        }

        String eventId = evento.eventId().toString();
        Optional<NotificacionEnviada> previa = notificacionRepository
                .findByEventIdAndCanal(eventId, CanalNotificacion.SMS);
        if (previa.isPresent() && previa.get().getEstado() == EstadoNotificacion.ENVIADA) {
            log.debug("SMS ya enviado eventId={}", eventId);
            return;
        }

        String cuerpo = plantilla.cuerpoSms(hito.numeroTracking(), resumenCorto(hito.estadoParaNotificar()));
        PlantillaMensaje.Mensaje msgSms = new PlantillaMensaje.Mensaje(null, cuerpo);

        NotificacionEnviada notif = previa.orElseGet(() -> persistirPendiente(
                eventId, hito, cliente, CanalNotificacion.SMS, telefono, msgSms));

        try {
            smsSender.enviar(telefono, cuerpo);
            marcarEnviada(notif);
        } catch (RuntimeException ex) {
            manejarFallo(notif, eventId, "SMS", ex);
        }
    }

    // ---------- Persistencia ----------

    @Transactional
    protected NotificacionEnviada persistirPendiente(String eventId, HitoNotificacion hito,
                                                     ClienteSnapshot cliente, CanalNotificacion canal,
                                                     String destinatario, PlantillaMensaje.Mensaje msg) {
        return notificacionRepository.save(NotificacionEnviada.pendiente(
                eventId, hito.tipoEvento(), cliente.id(), hito.pedidoId(), hito.numeroTracking(),
                canal, destinatario, msg.asunto(), msg.cuerpo()));
    }

    @Transactional
    protected void marcarEnviada(NotificacionEnviada notif) {
        notif.marcarEnviada();
        notificacionRepository.save(notif);
    }

    @Transactional
    protected int registrarFallo(NotificacionEnviada notif, String motivo) {
        notif.registrarFallo(motivo);
        notificacionRepository.save(notif);
        return notif.getIntentos();
    }

    // ---------- Reintentos y DLQ ----------

    private void manejarFallo(NotificacionEnviada notif, String eventId, String canal, RuntimeException ex) {
        int intentos = registrarFallo(notif, ex.getMessage());
        if (intentos >= maxIntentos) {
            log.error("Notificación agotó {} intentos. eventId={} canal={}. Va a DLQ.",
                    maxIntentos, eventId, canal, ex);
            publicarEnDlq(notif, eventId);
            // No relanzar: el contenedor de RabbitMQ hará ack y no reencolará.
            return;
        }
        log.warn("Fallo de notificación (intento {} de {}). eventId={} canal={}. Se reintentará.",
                intentos, maxIntentos, eventId, canal, ex);
        throw ex; // dispara el DLX a notification.retry.queue
    }

    private void publicarEnDlq(NotificacionEnviada notif, String eventId) {
        try {
            rabbitTemplate.convertAndSend("", NotificacionRabbitConfig.DLQ,
                    "FALLO_DEFINITIVO eventId=" + eventId
                            + " pedido=" + notif.getPedidoId()
                            + " canal=" + notif.getCanal()
                            + " intentos=" + notif.getIntentos()
                            + " motivo=" + notif.getMotivoFallo());
        } catch (RuntimeException ex) {
            log.error("No se pudo publicar en DLQ eventId={}", eventId, ex);
        }
    }

    // ---------- Helpers ----------

    @Transactional(readOnly = true)
    protected ClienteSnapshot resolverCliente(Long clienteId) {
        return clienteRepository.findById(clienteId)
                .map(c -> new ClienteSnapshot(
                        c.getId(),
                        c.getUsuario().getEmail(),
                        c.getUsuario().getTelefono()))
                .orElse(null);
    }

    private PlantillaMensaje.Mensaje construirMensaje(HitoNotificacion hito) {
        return hito.mensajeCliente() != null
                ? plantilla.porMensajeCliente(hito.numeroTracking(), hito.mensajeCliente())
                : plantilla.porEstado(hito.numeroTracking(), hito.estadoParaNotificar());
    }

    private static String resumenCorto(EstadoPedido estado) {
        return switch (estado) {
            case EN_TRANSITO -> "en tránsito.";
            case EN_REPARTO -> "en reparto.";
            case ENTREGADO -> "entregado.";
            case ENTREGA_FALLIDA -> "entrega fallida.";
            case ENTREGA_FALLIDA_VERIFICACION_EDAD -> "entrega no completada (verificación de edad).";
            case ENTREGA_REPROGRAMADA -> "reprogramado.";
            default -> "actualizado.";
        };
    }

    private record ClienteSnapshot(Long id, String email, String telefono) {}
}
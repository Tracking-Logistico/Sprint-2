package com.udea.demo.usuarios.application.service;

import com.udea.demo.pedidos.domain.event.DevolucionCreadaEvent;
import com.udea.demo.pedidos.domain.event.EnvioGestionEvent;
import com.udea.demo.usuarios.interfaces.services.EmailServiceI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnProperty(name = "app.messaging.rabbitmq.enabled", havingValue = "true")
public class NotificacionPartesEnvioListener {
    private static final Logger log = LoggerFactory.getLogger(NotificacionPartesEnvioListener.class);

    private final EmailServiceI emailService;
    private final JdbcTemplate jdbc;

    public NotificacionPartesEnvioListener(EmailServiceI emailService, JdbcTemplate jdbc) {
        this.emailService = emailService;
        this.jdbc = jdbc;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void notificar(EnvioGestionEvent evento) {
        enviarAlDestinatario(evento.correoDestinatario(), evento.numeroTracking(), evento.motivo());
        notificarConductor(evento.conductorUsuarioId(), evento.pedidoId(), evento.eventId().toString(),
                "El envío " + evento.numeroTracking() + " fue " + evento.estado().name().toLowerCase()
                        .replace('_', ' ') + ".");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void notificar(DevolucionCreadaEvent evento) {
        notificarConductor(evento.conductorUsuarioId(), evento.pedidoOrigenId(), evento.eventId().toString(),
                "Se inició la devolución " + evento.numeroTrackingOrigen() + ". Nuevo tracking: "
                        + evento.numeroTracking() + ".");
    }

    private void enviarAlDestinatario(String correo, String tracking, String mensaje) {
        if (correo == null || correo.isBlank()) return;
        try {
            emailService.enviarNotificacionCambioEstado(correo,
                    "Actualización de tu envío " + tracking, mensaje);
        } catch (RuntimeException ex) {
            log.error("No fue posible notificar a una parte del envío tracking={}", tracking, ex);
        }
    }

    private void notificarConductor(Long conductorUsuarioId, Long pedidoId, String eventId, String mensaje) {
        if (conductorUsuarioId == null) return;
        try {
            jdbc.update("""
                    INSERT INTO notificaciones_conductor (id_usuario_conductor, id_pedido, mensaje, event_id)
                    VALUES (?, ?, ?, ?)
                    """, conductorUsuarioId, pedidoId, mensaje, eventId);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Notificación de conductor duplicada eventId={}", eventId);
        } catch (RuntimeException ex) {
            log.error("No fue posible registrar la notificación del conductor eventId={}", eventId, ex);
        }
    }
}

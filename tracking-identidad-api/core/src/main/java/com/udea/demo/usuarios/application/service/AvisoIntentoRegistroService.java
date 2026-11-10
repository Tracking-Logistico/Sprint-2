package com.udea.demo.usuarios.application.service;

import com.udea.demo.usuarios.domain.model.Usuario;
import com.udea.demo.usuarios.interfaces.services.EmailServiceI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HU-13 (13-BE-01): avisa al titular de una cédula cuando alguien intenta registrar otra cuenta con ella.
 *
 * <ul>
 *   <li><b>Anti-enumeración.</b> Quien se registra recibe la misma respuesta neutra que en un registro nuevo.
 *       Solo el titular, en su propio correo, se entera del intento.</li>
 *   <li><b>Minimización.</b> El aviso no incluye la cédula ni el correo de quien lo intentó.</li>
 *   <li><b>Anti-abuso.</b> Como máximo un aviso por titular cada {@code app.registro.aviso-titular-minutos},
 *       para que el formulario no sirva para inundar de correos a una persona.</li>
 *   <li><b>Sin oráculo.</b> Si falla el envío, el error se registra de forma genérica y no se propaga.</li>
 * </ul>
 */
@Service
public class AvisoIntentoRegistroService {
    private static final Logger log = LoggerFactory.getLogger(AvisoIntentoRegistroService.class);
    static final String ASUNTO = "Intento de registro con tus datos de identidad";
    static final String CONTENIDO = "Recibimos una solicitud para crear una cuenta en LogisTrack con un número de cédula "
            + "que ya está asociado a tu cuenta. No se creó ninguna cuenta nueva. Si fuiste tú, inicia sesión o "
            + "recupera tu contraseña. Si no reconoces esta solicitud, no necesitas hacer nada.";
    private static final int MAX_ENTRADAS = 10_000;

    private final EmailServiceI emailService;
    private final Clock reloj;
    private final Duration intervalo;
    private final Map<Long, Instant> ultimoAviso = new ConcurrentHashMap<>();

    @Autowired
    public AvisoIntentoRegistroService(EmailServiceI emailService,
                                       @Value("${app.registro.aviso-titular-minutos:60}") long minutosEntreAvisos) {
        this(emailService, Clock.systemUTC(), Duration.ofMinutes(minutosEntreAvisos));
    }

    AvisoIntentoRegistroService(EmailServiceI emailService, Clock reloj, Duration intervalo) {
        this.emailService = emailService;
        this.reloj = reloj;
        this.intervalo = intervalo;
    }

    /** Devuelve true si se envió (o se intentó enviar) el aviso; false si se omitió por el límite de frecuencia. */
    public boolean avisarTitular(Usuario titular) {
        if (titular == null || titular.getId() == null || titular.getEmail() == null) return false;
        Instant ahora = reloj.instant();
        boolean[] enviar = {false};
        ultimoAviso.compute(titular.getId(), (id, anterior) -> {
            if (anterior == null || !ahora.isBefore(anterior.plus(intervalo))) {
                enviar[0] = true;
                return ahora;
            }
            return anterior;
        });
        if (!enviar[0]) return false;
        if (ultimoAviso.size() > MAX_ENTRADAS) {
            ultimoAviso.entrySet().removeIf(e -> !ahora.isBefore(e.getValue().plus(intervalo)));
        }
        try {
            emailService.enviarNotificacionCambioEstado(titular.getEmail(), ASUNTO, CONTENIDO);
        } catch (RuntimeException ex) {
            log.warn("No fue posible enviar el aviso de intento de registro usuarioId={}", titular.getId());
        }
        return true;
    }
}

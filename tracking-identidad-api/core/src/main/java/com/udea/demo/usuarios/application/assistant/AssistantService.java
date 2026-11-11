package com.udea.demo.usuarios.application.assistant;

import com.udea.demo.usuarios.interfaces.persistence.UsuarioRepository;
import com.udea.demo.usuarios.domain.model.Rol;
import com.udea.demo.usuarios.domain.model.EstadoUsuario;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import java.time.Instant;
import java.util.concurrent.Semaphore;
import static com.udea.demo.usuarios.application.assistant.AssistantDTO.*;

@Service
@ConditionalOnProperty(name="app.assistant.enabled",havingValue="true")
public class AssistantService {
    private final AssistantGuard guard;
    private final GeminiIntentClient gemini;
    private final AssistantReadStore reader;
    private final AssistantAudit audit;
    private final UsuarioRepository users;
    private final Semaphore active=new Semaphore(8);
    public static final String OUTSIDE="Esa información está fuera de mi alcance autorizado. Puedo consultar perfiles individuales de usuario y el estado o última ubicación de un paquete. Prueba con «¿Dónde está el paquete CÓDIGO?» o «¿Qué rol tiene NOMBRE?»";

    public AssistantService(AssistantGuard guard,GeminiIntentClient gemini,AssistantReadStore reader,AssistantAudit audit,UsuarioRepository users) {
        this.guard=guard;this.gemini=gemini;this.reader=reader;this.audit=audit;this.users=users;
    }

    public Answer query(long user,Query query) {
        var actor=users.findById(user).orElseThrow(()->new AccessDeniedException("Acceso restringido"));
        if (actor.getRol()!=Rol.OPERADOR || !Boolean.TRUE.equals(actor.getActivo()) || actor.getEstado()!=EstadoUsuario.ACTIVO) throw new AccessDeniedException("Acceso restringido");
        if (!active.tryAcquire()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
        try {
            var session=audit.reserve(user,query,Instant.now());
            var intent=Intent.FUERA_DE_ALCANCE;
            try {
                Prepared prepared=guard.prepare(query.texto());
                intent=prepared.intent();
                if (intent!=Intent.FUERA_DE_ALCANCE) intent=gemini.classify(prepared);
                if (intent!=prepared.intent()) intent=Intent.FUERA_DE_ALCANCE;
                Lookup lookup=intent==Intent.FUERA_DE_ALCANCE ? new Lookup(Result.FUERA_DE_ALCANCE,OUTSIDE) : reader.lookup(prepared);
                audit.complete(user,query.solicitudId(),intent,lookup.result());
                return new Answer(session,lookup.result(),lookup.text());
            } catch (RuntimeException ex) {
                audit.complete(user,query.solicitudId(),intent,Result.ERROR);
                throw new AssistantUnavailableException();
            }
        } finally { active.release(); }
    }
}

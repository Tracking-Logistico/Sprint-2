package com.udea.demo.pedidos.application.anomalias;

import com.udea.demo.usuarios.application.service.ActorAuthorizationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

@Component
public class AccesoAnomalias {
    private final ActorAuthorizationService actores;
    private final JdbcTemplate jdbc;
    private final String claveDba;
    private final TransactionTemplate limites;

    public AccesoAnomalias(ActorAuthorizationService actores,JdbcTemplate jdbc,
            @Value("${app.dba.api-key:}") String claveDba,PlatformTransactionManager transacciones) {
        this.actores=actores;
        this.jdbc=jdbc;
        this.claveDba=claveDba;
        this.limites=new TransactionTemplate(transacciones);
        this.limites.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public long operador() {
        actores.operadorActualId();
        return actores.actorActual().getId();
    }

    public void exigirDba(String suministrada) {
        if (claveDba.isBlank() || suministrada==null || suministrada.length()>512
                || !MessageDigest.isEqual(claveDba.getBytes(StandardCharsets.UTF_8),suministrada.getBytes(StandardCharsets.UTF_8))) {
            throw new AccessDeniedException("Autorización administrativa requerida");
        }
    }

    public void limitar(long usuario,Instant ahora) {

        Integer usos=limites.execute(status -> {
            jdbc.queryForObject("SELECT id FROM usuarios WHERE id=? FOR UPDATE",Long.class,usuario);
            long minuto=ahora.getEpochSecond()/60;
            var actuales=jdbc.query("SELECT minuto,peticiones FROM limite_anomalias WHERE usuario_id=?",
                    (r,n)->new long[]{r.getLong(1),r.getLong(2)},usuario);
            if (actuales.isEmpty()) {
                jdbc.update("INSERT INTO limite_anomalias VALUES (?,?,1)",usuario,minuto);
                return 1;
            }
            int siguiente=actuales.get(0)[0]==minuto ? (int)Math.min(61,actuales.get(0)[1]+1) : 1;
            jdbc.update("UPDATE limite_anomalias SET minuto=?,peticiones=? WHERE usuario_id=?",minuto,siguiente,usuario);
            return siguiente;
        });
        if (usos!=null && usos>60) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
    }
}

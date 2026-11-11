package com.udea.demo.usuarios.application.assistant;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import javax.sql.DataSource;
import java.time.*;
import java.util.UUID;
import static com.udea.demo.usuarios.application.assistant.AssistantDTO.*;

@Component
@ConditionalOnProperty(name="app.assistant.enabled",havingValue="true")
public class AssistantAudit {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final int retentionDays;

    public AssistantAudit(DataSource dataSource,PlatformTransactionManager manager,
                          @Value("${app.assistant.retention-days:30}") int retentionDays) {
        if (retentionDays<1 || retentionDays>365) throw new IllegalArgumentException("Retención inválida");
        this.retentionDays=retentionDays;
        jdbc=new JdbcTemplate(dataSource);jdbc.setQueryTimeout(1);
        transaction=new TransactionTemplate(manager);transaction.setTimeout(2);
    }

    public UUID reserve(long user,Query query,Instant now) {
        UUID result=transaction.execute(status -> {
            jdbc.queryForObject("SELECT id FROM usuarios WHERE id=? FOR UPDATE",Long.class,user);
            long minute=now.getEpochSecond()/60;
            var previous=jdbc.query("SELECT minuto,peticiones FROM assistant_rate_limit WHERE usuario_id=?",(r,n)->new long[]{r.getLong(1),r.getInt(2)},user);
            int attempts=previous.isEmpty() || previous.get(0)[0]!=minute ? 1 : (int)Math.min(31,previous.get(0)[1]+1);
            if (previous.isEmpty()) jdbc.update("INSERT INTO assistant_rate_limit VALUES (?,?,?)",user,minute,attempts);
            else jdbc.update("UPDATE assistant_rate_limit SET minuto=?,peticiones=? WHERE usuario_id=?",minute,attempts,user);
            if (attempts>30) return null;
            UUID session=query.sessionId();
            if (session==null) {
                session=UUID.randomUUID();
                jdbc.update("INSERT INTO assistant_session VALUES (?,?,?,?)",session,user,utc(now),utc(now.plusSeconds(1800)));
            } else {
                Integer valid=jdbc.queryForObject("SELECT count(*) FROM assistant_session WHERE id=? AND usuario_id=? AND expira_en>?",Integer.class,session,user,utc(now));
                if (valid==null || valid!=1) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            Integer used=jdbc.queryForObject("SELECT count(*) FROM assistant_query_log WHERE usuario_id=? AND solicitud_id=?",Integer.class,user,query.solicitudId());
            if (used!=null && used>0) throw new ResponseStatusException(HttpStatus.CONFLICT);
            jdbc.update("INSERT INTO assistant_query_log VALUES (?,?,?,?,'FUERA_DE_ALCANCE','ERROR',?)",
                    query.solicitudId(),session,user,utc(now),query.origen().name());
            return session;
        });
        if (result==null) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        return result;
    }

    public void complete(long user,UUID request,Intent intent,Result result) {
        transaction.executeWithoutResult(status -> jdbc.update("UPDATE assistant_query_log SET intencion=?,resultado=? WHERE usuario_id=? AND solicitud_id=?",
                intent.name(),result.name(),user,request));
    }

    @Scheduled(fixedDelayString="${app.assistant.cleanup-ms:3600000}",initialDelayString="${app.assistant.cleanup-ms:3600000}")
    public void cleanup() { cleanup(Instant.now()); }

    public void cleanup(Instant now) {
        transaction.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM assistant_query_log WHERE fecha<?",utc(now.minusSeconds(retentionDays*86400L)));
            jdbc.update("DELETE FROM assistant_session s WHERE expira_en<? AND NOT EXISTS (SELECT 1 FROM assistant_query_log q WHERE q.session_id=s.id)",utc(now));
            jdbc.update("DELETE FROM assistant_rate_limit WHERE minuto<?",now.minusSeconds(retentionDays*86400L).getEpochSecond()/60);
        });
    }

    private OffsetDateTime utc(Instant now) { return now.atOffset(ZoneOffset.UTC); }
}

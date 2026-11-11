package com.udea.demo.usuarios.application.assistant;

import com.udea.demo.usuarios.domain.model.CampoIdentidad;
import com.udea.demo.usuarios.domain.model.NumeroCedula;
import com.udea.demo.usuarios.interfaces.services.CifradorDatosIdentidadI;
import com.zaxxer.hikari.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import java.sql.*;
import java.util.Locale;
import static com.udea.demo.usuarios.application.assistant.AssistantDTO.*;

@Component
@ConditionalOnProperty(name="app.assistant.enabled",havingValue="true")
public class AssistantReadStore {
    private final HikariDataSource pool;
    private final CifradorDatosIdentidadI cifrador;

    public AssistantReadStore(@Value("${app.assistant.reader.url:}") String url,
            @Value("${app.assistant.reader.user:}") String user,@Value("${app.assistant.reader.password:}") String password,
            @Value("${app.assistant.reader.require-tls:true}") boolean tls,Environment environment,CifradorDatosIdentidadI cifrador) {
        if (!url.startsWith("jdbc:postgresql://") || user.isBlank() || password.isBlank()) throw new IllegalArgumentException("Configura la conexión de lectura del asistente");
        if (tls && !url.matches(".*[?&]sslmode=verify-full(?:&.*)?$")) throw new IllegalArgumentException("El asistente requiere TLS verificado");
        if (!tls && !environment.acceptsProfiles(Profiles.of("local","test","h2"))) throw new IllegalArgumentException("TLS requerido");
        HikariConfig config=new HikariConfig();
        config.setJdbcUrl(url); config.setUsername(user); config.setPassword(password);
        config.setPoolName("assistant-reader"); config.setMaximumPoolSize(3); config.setMinimumIdle(0);
        config.setConnectionTimeout(500); config.setValidationTimeout(250); config.setInitializationFailTimeout(-1);
        config.setReadOnly(true); config.addDataSourceProperty("connectTimeout","2"); config.addDataSourceProperty("socketTimeout","2");
        pool=new HikariDataSource(config); this.cifrador=cifrador;
    }

    public Lookup lookup(Prepared query) {
        try (Connection c=pool.getConnection()) {
            c.setReadOnly(true); c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); c.setAutoCommit(false);
            try {
                verifyPermissions(c);
                return query.intent()==Intent.PERFIL ? profile(c,query.target()) : parcel(c,query.target());
            } finally { c.rollback(); }
        } catch (SQLException ex) { throw new AssistantUnavailableException(); }
    }

    private void verifyPermissions(Connection c) throws SQLException {
        try (var s=c.prepareStatement("""
                SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname=current_user
                  AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolreplication OR rolbypassrls))
                  OR has_column_privilege(current_user,'usuarios','password','SELECT')
                  OR has_schema_privilege(current_user,current_schema(),'CREATE')
                  OR EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                    WHERE n.nspname NOT LIKE 'pg_%' AND n.nspname<>'information_schema'
                    AND c.relkind IN ('r','p','v','f')
                    AND (has_table_privilege(current_user,c.oid,'INSERT,UPDATE,DELETE,TRUNCATE')
                      OR has_any_column_privilege(current_user,c.oid,'INSERT,UPDATE')
                      OR (c.relname NOT IN ('assistant_user_profile','assistant_package','assistant_location')
                        AND (has_table_privilege(current_user,c.oid,'SELECT')
                          OR has_any_column_privilege(current_user,c.oid,'SELECT')))))
                """)) {
            s.setQueryTimeout(1);
            try (var r=s.executeQuery()) { if (!r.next() || r.getBoolean(1)) throw new AssistantUnavailableException(); }
        }
    }

    private Lookup profile(Connection c,String target) throws SQLException {
        try (var s=c.prepareStatement("""
                SELECT nombre,rol,estado,email,telefono,numero_cedula_cifrado FROM assistant_user_profile
                WHERE lower(nombre)=lower(?) OR lower(email)=lower(?) ORDER BY id LIMIT 2
                """)) {
            s.setQueryTimeout(1); s.setString(1,target); s.setString(2,target);
            try (var r=s.executeQuery()) {
                if (!r.next()) return new Lookup(Result.SIN_DATOS,"No encontré un usuario con ese nombre o correo.");
                String nombre=r.getString(1),rol=r.getString(2),estado=r.getString(3),correo=r.getString(4),telefono=r.getString(5),cipher=r.getString(6);
                if (r.next()) return new Lookup(Result.SIN_DATOS,"Coinciden varios usuarios. Especifica el correo del usuario que quieres consultar.");
                String document=null;
                if (cipher!=null) document=NumeroCedula.de(cifrador.descifrar(cipher,CampoIdentidad.NUMERO_CEDULA)).valor();
                String mask=document==null ? "No registrada" : NumeroCedula.de(document).enmascarado();
                String text="Nombre — "+nombre+"\nRol — "+human(rol)+"\nEstado de cuenta — "+human(estado)
                        +"\nCorreo — "+correo+"\nTeléfono — "+(telefono==null ? "No registrado" : telefono)+"\nCédula — "+mask;
                if (document!=null) text=text.replace(document,mask);
                return new Lookup(Result.RESUELTA,text);
            }
        }
    }

    private Lookup parcel(Connection c,String tracking) throws SQLException {
        long id; String estado,code;
        try (var s=c.prepareStatement("SELECT id_pedido,estado,numero_tracking FROM assistant_package WHERE numero_tracking=?")) {
            s.setString(1,tracking);s.setQueryTimeout(1);
            try (var r=s.executeQuery()) {
                if (!r.next()) return new Lookup(Result.SIN_DATOS,"No encontré un paquete con ese código de seguimiento.");
                id=r.getLong(1); estado=r.getString(2); code=r.getString(3);
            }
        }
        String location="Sin ubicación registrada";
        try (var s=c.prepareStatement("""
                SELECT etapa,oficina_nombre,vehiculo_id,punto_entrega_nombre,ciudad_nombre
                FROM assistant_location WHERE id_pedido=? ORDER BY fecha_evento DESC,id DESC LIMIT 1
                """)) {
            s.setLong(1,id);s.setQueryTimeout(1);
            try (var r=s.executeQuery()) {
                if (r.next()) location=switch (r.getString(1)) {
                    case "EN_TRANSITO_VEHICULO" -> value("Vehículo",r.getString(3));
                    case "EN_PUNTO_ENTREGA" -> value("Punto de entrega",r.getString(4));
                    case "EN_BODEGA_ORIGEN","LLEGADO_OFICINA_DESTINO" -> value("Oficina",r.getString(2));
                    default -> "Sin ubicación registrada";
                };
            }
        }
        return new Lookup(Result.RESUELTA,"Paquete — "+code+"\nEstado actual — "+human(estado)+"\nÚltima ubicación registrada — "+location);
    }

    private String value(String label,String text) { return text==null || text.isBlank() ? "Sin ubicación registrada" : label+" "+text; }
    private String human(String value) { return value==null ? "No registrado" : value.replace('_',' ').toLowerCase(Locale.forLanguageTag("es")); }
    @PreDestroy public void close() { pool.close(); }
}

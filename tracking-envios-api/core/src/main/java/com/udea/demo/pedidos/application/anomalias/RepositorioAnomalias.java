package com.udea.demo.pedidos.application.anomalias;

import com.udea.demo.pedidos.domain.anomalias.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import static com.udea.demo.pedidos.application.anomalias.AnomaliasDTO.*;

@Repository
public class RepositorioAnomalias {
    private final JdbcTemplate jdbc;

    private static final String CONSULTA = """
            SELECT a.*, p.numero_tracking, o.nombre AS oficina
            FROM anomalias a JOIN pedidos p ON p.id_pedido=a.envio_id
            LEFT JOIN oficinas_logisticas o ON o.id=a.oficina_id
            """;

    public RepositorioAnomalias(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Parametros parametros() {
        return jdbc.queryForObject("SELECT * FROM parametros_anomalia WHERE id=1",
                (r, n) -> new Parametros(r.getInt("permanencia_horas"), r.getInt("inactividad_horas"),
                        r.getInt("recepcion_horas"), r.getLong("version")));
    }

    public Parametros actualizar(CambiarParametros dto, long usuario, Instant ahora) {
        int actualizados = jdbc.update("""
                UPDATE parametros_anomalia SET permanencia_horas=?, inactividad_horas=?, recepcion_horas=?,
                version=version+1, modificado_por=?, modificado_en=?, origen=? WHERE id=1 AND version=?
                """, dto.permanenciaHoras(), dto.inactividadHoras(), dto.recepcionHoras(), usuario,
                utc(ahora), dto.origen().name(), dto.version());
        if (actualizados != 1) throw new ResponseStatusException(HttpStatus.CONFLICT);
        jdbc.update("""
                INSERT INTO historial_parametros_anomalia
                (permanencia_horas,inactividad_horas,recepcion_horas,version,usuario_id,fecha,origen)
                VALUES (?,?,?,?,?,?,?)
                """, dto.permanenciaHoras(), dto.inactividadHoras(), dto.recepcionHoras(), dto.version()+1,
                usuario, utc(ahora), dto.origen().name());
        return parametros();
    }


    public void reconciliar(long envio, TipoAnomalia tipo, CondicionAnomalia condicion, Instant ahora) {
        List<Anomalia> activas = jdbc.query(CONSULTA + " WHERE a.envio_id=? AND a.tipo=? AND a.estado='ACTIVA'",
                MAPPER, envio, tipo.name());
        for (Anomalia activa : activas) {
            if (condicion == null || !condicion.eventoOrigen().equals(activa.eventoOrigen())) {
                cerrar(activa.id(), EstadoAnomalia.RESUELTA, null, "SISTEMA", ahora);
            }
        }
        if (condicion == null) return;
        jdbc.update("""
                INSERT INTO anomalias (envio_id,tipo,evento_origen,oficina_id,inicio_utc,umbral_horas,
                intentos,estado,clave_activa,creada_en)
                SELECT ?,?,?,?,?,?,?,'ACTIVA',1,? WHERE NOT EXISTS
                (SELECT 1 FROM anomalias WHERE envio_id=? AND tipo=? AND evento_origen=?)
                """, envio, tipo.name(), condicion.eventoOrigen(), condicion.oficinaId(), utc(condicion.inicio()),
                condicion.umbralHoras(), condicion.intentos(), utc(ahora), envio, tipo.name(), condicion.eventoOrigen());
    }

    public void cerrar(long id, EstadoAnomalia estado, Long usuario, String origen, Instant ahora) {
        jdbc.update("""
                UPDATE anomalias SET estado=?, clave_activa=NULL, cerrada_en=?, cerrada_por=?, origen_cierre=?
                WHERE id=? AND estado='ACTIVA'
                """, estado.name(), utc(ahora), usuario, origen, id);
    }

    public Anomalia obtener(long id) {
        return jdbc.query(CONSULTA + " WHERE a.id=?", MAPPER, id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public Pagina listar(EstadoAnomalia estado, TipoAnomalia tipo, int pagina, int tamano) {

        String filtro = " WHERE a.estado=?" + (tipo == null ? "" : " AND a.tipo=?");
        Object[] valores = tipo == null ? new Object[]{estado.name()} : new Object[]{estado.name(), tipo.name()};
        Long total = jdbc.queryForObject("SELECT count(*) FROM anomalias a"+filtro, Long.class, valores);
        var args = new java.util.ArrayList<Object>(List.of(valores));
        args.add(tamano);
        args.add((long) pagina * tamano);
        List<Anomalia> filas = jdbc.query(CONSULTA+filtro+" ORDER BY a.id DESC LIMIT ? OFFSET ?", MAPPER, args.toArray());
        return new Pagina(filas, pagina, tamano, total == null ? 0 : total);
    }

    public long ultimoPedido() {
        return jdbc.queryForObject("SELECT COALESCE(MAX(id_pedido),0) FROM pedidos",Long.class);
    }

    public List<Long> siguientes(long despues, long hasta, int limite) {
        return jdbc.query("""
                SELECT p.id_pedido FROM pedidos p WHERE p.id_pedido > ? AND p.id_pedido <= ? AND
                (p.estado IN ('CREADO','RECIBIDO_EN_ORIGEN','EN_TRANSITO','EN_REPARTO',
                'ENTREGA_FALLIDA','ENTREGA_REPROGRAMADA','DIRECCION_POR_VERIFICAR')
                OR EXISTS (SELECT 1 FROM anomalias a WHERE a.envio_id=p.id_pedido AND a.estado='ACTIVA'))
                ORDER BY p.id_pedido LIMIT ?
                """, (r,n) -> r.getLong(1), despues, hasta, limite);
    }

    public List<Anomalia> activas(long envio) {
        return jdbc.query(CONSULTA+" WHERE a.envio_id=? AND a.estado='ACTIVA' ORDER BY a.id", MAPPER, envio);
    }

    static OffsetDateTime utc(Instant instant) { return instant.atOffset(ZoneOffset.UTC); }

    private static Instant instante(ResultSet r, String columna) throws SQLException {
        OffsetDateTime fecha = r.getObject(columna, OffsetDateTime.class);
        return fecha == null ? null : fecha.toInstant();
    }

    private static final RowMapper<Anomalia> MAPPER = (r,n) -> new Anomalia(r.getLong("id"),
            r.getLong("envio_id"), r.getString("numero_tracking"), TipoAnomalia.valueOf(r.getString("tipo")),
            EstadoAnomalia.valueOf(r.getString("estado")), r.getString("evento_origen"),
            r.getObject("oficina_id", Long.class), r.getString("oficina"), instante(r,"inicio_utc"),
            r.getObject("umbral_horas",Integer.class), r.getObject("intentos",Integer.class),
            instante(r,"creada_en"), instante(r,"cerrada_en"), r.getObject("cerrada_por",Long.class),
            r.getString("origen_cierre"));
}

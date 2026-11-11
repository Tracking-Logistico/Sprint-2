package com.udea.demo.pedidos.application.anomalias;

import com.udea.demo.pedidos.domain.anomalias.*;
import com.udea.demo.pedidos.application.service.AccesoClientePedido;
import com.udea.demo.pedidos.interfaces.persistence.PedidoRepository;
import com.udea.demo.usuarios.application.service.ActorAuthorizationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.util.List;
import static com.udea.demo.pedidos.application.anomalias.AnomaliasDTO.*;

@Service
public class GestionAnomaliasService {
    private final RepositorioAnomalias anomalias;
    private final PedidoRepository pedidos;
    private final AccesoAnomalias acceso;
    private final AccesoClientePedido clientes;
    private final ActorAuthorizationService actores;
    private final ReglasAnomalias reglas;
    private final JdbcTemplate jdbc;
    private final Clock reloj;
    private final String telefonoGeneral;

    public GestionAnomaliasService(RepositorioAnomalias anomalias,PedidoRepository pedidos,AccesoAnomalias acceso,
            AccesoClientePedido clientes,ActorAuthorizationService actores,ReglasAnomalias reglas,JdbcTemplate jdbc,
            @Qualifier("relojAnomalias") Clock reloj,@Value("${app.anomalias.contacto-general:}") String telefonoGeneral) {
        if (!telefonoGeneral.isBlank() && telefonoSeguro(telefonoGeneral)==null) {
            throw new IllegalArgumentException("El contacto general debe tener formato telefónico internacional");
        }
        this.anomalias=anomalias; this.pedidos=pedidos; this.acceso=acceso; this.clientes=clientes;
        this.actores=actores; this.reglas=reglas; this.jdbc=jdbc; this.reloj=reloj;
        this.telefonoGeneral=telefonoSeguro(telefonoGeneral);
    }

    public Pagina listar(EstadoAnomalia estado,TipoAnomalia tipo,int pagina,int tamano) {
        acceso.limitar(acceso.operador(),reloj.instant());
        if (pagina<0 || pagina>100000 || tamano<1 || tamano>100) throw new IllegalArgumentException("Paginación inválida");
        return anomalias.listar(estado,tipo,pagina,tamano);
    }

    public Anomalia detalle(long id) {
        acceso.limitar(acceso.operador(),reloj.instant());
        return anomalias.obtener(id);
    }

    public List<Llegada> recepcionesPendientes(int pagina) {
        acceso.limitar(acceso.operador(),reloj.instant());
        if (pagina<0 || pagina>100000) throw new IllegalArgumentException("Página inválida");
        return jdbc.query("""
                SELECT p.id_pedido,e.id,p.numero_tracking,o.nombre,e.fecha_evento
                FROM eventos_ubicacion_pedido e JOIN pedidos p ON p.id_pedido=e.id_pedido
                LEFT JOIN oficinas_logisticas o ON o.id=e.oficina_id
                WHERE e.etapa='LLEGADO_OFICINA_DESTINO' AND p.estado='EN_TRANSITO'
                AND NOT EXISTS (SELECT 1 FROM recepciones_oficina r WHERE r.evento_llegada_id=e.id)
                AND NOT EXISTS (SELECT 1 FROM eventos_ubicacion_pedido c WHERE c.corrige_evento_id=e.id)
                AND NOT EXISTS (SELECT 1 FROM eventos_ubicacion_pedido m WHERE m.id_pedido=e.id_pedido
                  AND (m.fecha_evento>e.fecha_evento OR (m.fecha_evento=e.fecha_evento AND m.id>e.id))
                  AND NOT EXISTS (SELECT 1 FROM eventos_ubicacion_pedido c WHERE c.corrige_evento_id=m.id))
                ORDER BY e.fecha_evento,e.id LIMIT 20 OFFSET ?
                """,(r,n)->new Llegada(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),
                        r.getObject(5,java.time.LocalDateTime.class)),pagina*20L);
    }

    @Transactional
    public Anomalia cerrar(long id,Cerrar dto) {
        long usuario=acceso.operador();
        acceso.limitar(usuario,reloj.instant());
        if (dto.estado()==EstadoAnomalia.ACTIVA) throw new IllegalArgumentException("Transición inválida");
        var previa=anomalias.obtener(id);
        pedidos.findByIdForUpdate(previa.envioId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var actual=anomalias.obtener(id);
        if (actual.estado()==dto.estado()) return actual;
        if (actual.estado()!=EstadoAnomalia.ACTIVA) throw new ResponseStatusException(HttpStatus.CONFLICT);
        anomalias.cerrar(id,dto.estado(),usuario,dto.origen().name(),reloj.instant());
        return anomalias.obtener(id);
    }

    public Parametros parametros(String clave) {
        acceso.limitar(acceso.operador(),reloj.instant());
        acceso.exigirDba(clave);
        return anomalias.parametros();
    }

    @Transactional
    public Parametros cambiar(CambiarParametros dto,String clave) {
        long usuario=acceso.operador();
        acceso.limitar(usuario,reloj.instant());
        acceso.exigirDba(clave);
        return anomalias.actualizar(dto,usuario,reloj.instant());
    }

    @Transactional
    public void confirmarRecepcion(long envio,Recepcion dto) {
        long usuario=acceso.operador();
        acceso.limitar(usuario,reloj.instant());
        var pedido=pedidos.findByIdForUpdate(envio).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!pedido.getEstado().esActivoLogistico()) throw new ResponseStatusException(HttpStatus.CONFLICT);
        var llegada=reglas.ultimaUbicacion(envio);
        if (llegada==null || llegada.id()!=dto.eventoLlegadaId() || !llegada.etapa().equals("LLEGADO_OFICINA_DESTINO")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        jdbc.update("""
                INSERT INTO recepciones_oficina (evento_llegada_id,usuario_id,fecha,origen)
                SELECT ?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM recepciones_oficina WHERE evento_llegada_id=?)
                """,llegada.id(),usuario,RepositorioAnomalias.utc(reloj.instant()),dto.origen().name(),llegada.id());
        anomalias.reconciliar(envio,TipoAnomalia.SIN_CONFIRMACION_RECEPCION,null,reloj.instant());
    }

    @Transactional(readOnly=true)
    public List<AlertaCliente> alertasCliente(String tracking) {
        var pedido=clientes.pedidoDelClienteActual(tracking);
        acceso.limitar(actores.actorActual().getId(),reloj.instant());
        var parametros=anomalias.parametros();
        return anomalias.activas(pedido.getId()).stream().filter(a -> {
            var vigente=reglas.evaluar(pedido,a.tipo(),parametros,reloj.instant());
            return vigente!=null && vigente.eventoOrigen().equals(a.eventoOrigen());
        }).map(this::cliente).toList();
    }

    private AlertaCliente cliente(Anomalia a) {
        String telefono=null;
        if (a.oficinaId()!=null) {
            telefono=jdbc.query("SELECT telefono FROM oficinas_logisticas WHERE id=?",(r,n)->r.getString(1),a.oficinaId())
                    .stream().findFirst().map(GestionAnomaliasService::telefonoSeguro).orElse(null);
        }
        if (telefono==null) telefono=telefonoGeneral;
        return new AlertaCliente(a.tipo().mensajeCliente(),a.oficina()==null ? "Atención general" : a.oficina(),
                telefono,telefono==null ? null : "tel:"+telefono);
    }

    static String telefonoSeguro(String valor) {
        if (valor==null || !valor.matches("[+0-9 ()-]{7,30}")) return null;
        String telefono=valor.replaceAll("[ ()-]","");
        return telefono.matches("\\+?[1-9][0-9]{6,14}") ? telefono : null;
    }
}

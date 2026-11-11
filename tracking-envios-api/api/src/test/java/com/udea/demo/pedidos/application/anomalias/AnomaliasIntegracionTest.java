package com.udea.demo.pedidos.application.anomalias;

import com.udea.demo.pedidos.domain.model.*;
import com.udea.demo.pedidos.interfaces.persistence.*;
import com.udea.demo.pedidos.domain.anomalias.*;
import com.udea.demo.rutas.domain.model.Ruta;
import com.udea.demo.rutas.interfaces.persistence.RutaRepository;
import com.udea.demo.usuarios.domain.model.*;
import com.udea.demo.usuarios.interfaces.persistence.*;
import com.udea.demo.usuarios.application.service.AutenticacionService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import jakarta.servlet.Filter;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.api.role=pedidos","app.anomalias.enabled=true",
        "app.anomalias.inicio-ms=86400000","app.anomalias.contacto-general=+9990000000",
        "spring.datasource.url=jdbc:h2:mem:hu18;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts="classpath:db/h2/schema.sql",executionPhase=Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AnomaliasIntegracionTest {
    @Autowired WebApplicationContext contexto;
    @Autowired Filter springSecurityFilterChain;
    @Autowired JdbcTemplate jdbc;
    @Autowired PedidoRepository pedidos;
    @Autowired UsuarioRepository usuarios;
    @Autowired ClienteRepository clientes;
    @Autowired OperadorRepository operadores;
    @Autowired ConductorRepository conductores;
    @Autowired SesionUsuarioRepository sesiones;
    @Autowired RutaRepository rutas;
    @Autowired EventoUbicacionPedidoRepository ubicaciones;
    @Autowired MotorAnomalias motor;
    @Autowired RepositorioAnomalias alertas;
    @Autowired MonitoreoAnomaliasTask tarea;
    @MockitoBean(name="relojAnomalias") Clock reloj;
    MockMvc mvc;
    final Instant ahora=Instant.parse("2030-01-03T12:00:00Z");
    long clienteId, oficina, ciudad, operadorUsuario, conductorId;
    String operadorToken, clienteToken, destinatarioToken, destinatarioEmail, ajenoToken, conductorToken;

    @BeforeEach
    void preparar() {
        when(reloj.instant()).thenReturn(ahora);
        when(reloj.getZone()).thenReturn(ZoneOffset.UTC);
        jdbc.update("DELETE FROM anomalias");
        jdbc.update("DELETE FROM recepciones_oficina");
        jdbc.update("DELETE FROM historial_parametros_anomalia");
        jdbc.update("DELETE FROM limite_anomalias");
        jdbc.update("UPDATE parametros_anomalia SET permanencia_horas=24,inactividad_horas=4,recepcion_horas=2,version=0");
        mvc=MockMvcBuilders.webAppContextSetup(contexto).addFilters(springSecurityFilterChain).build();
        var operador=usuario(Rol.OPERADOR);
        operadores.saveAndFlush(new Operador(null,operador,"OP-"+UUID.randomUUID()));
        operadorUsuario=operador.getId(); operadorToken=sesion(operador);
        var remitente=usuario(Rol.CLIENTE);
        clienteId=clientes.saveAndFlush(new Cliente(null,remitente,"Ciudad sintética")).getId();
        clienteToken=sesion(remitente);
        var destinatario=usuario(Rol.CLIENTE);
        clientes.saveAndFlush(new Cliente(null,destinatario,"Ciudad sintética"));
        destinatarioToken=sesion(destinatario); destinatarioEmail=destinatario.getEmail();
        var ajeno=usuario(Rol.CLIENTE);
        clientes.saveAndFlush(new Cliente(null,ajeno,"Ciudad sintética")); ajenoToken=sesion(ajeno);
        var conductor=usuario(Rol.CONDUCTOR);
        conductorId=conductores.saveAndFlush(Conductor.builder().usuario(conductor).licencia(UUID.randomUUID().toString()).estado("ACTIVO").build()).getId();
        conductorToken=sesion(conductor);
        String sufijo=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ciudades_logisticas (nombre,departamento) VALUES (?,?)",sufijo,"Sintético");
        ciudad=jdbc.queryForObject("SELECT id FROM ciudades_logisticas WHERE nombre=?",Long.class,sufijo);
        jdbc.update("INSERT INTO oficinas_logisticas (nombre,ciudad_id,direccion,telefono) VALUES (?,?,?,?)",sufijo,ciudad,"Oficina de pruebas","+9990000001");
        oficina=jdbc.queryForObject("SELECT id FROM oficinas_logisticas WHERE nombre=?",Long.class,sufijo);
    }

    Usuario usuario(Rol rol) {
        return usuarios.saveAndFlush(Usuario.builder().nombre("Cuenta sintética").email(UUID.randomUUID()+"@example.invalid")
                .password("valor-sintetico-no-credencial").rol(rol).estado(EstadoUsuario.ACTIVO).activo(true).aceptoTerminos(false).build());
    }
    String sesion(Usuario u) {
        String token=UUID.randomUUID().toString();
        var t=LocalDateTime.now();
        sesiones.saveAndFlush(new SesionUsuario(u,AutenticacionService.hash(token),AutenticacionService.hash(UUID.randomUUID().toString()),t.plusHours(1),t.plusDays(1),t));
        return token;
    }
    Pedido pedido(EstadoPedido estado) {
        String numero=UUID.randomUUID().toString();
        return pedidos.saveAndFlush(Pedido.builder().numeroPedido("P-"+numero).numeroTracking("T-"+numero)
                .clienteId(clienteId).destinatarioEmail(destinatarioEmail).direccionOrigen("Origen sintético").direccionDestino("Destino sintético")
                .descripcionPaquete("Prueba").pesoKg(1.0).largoCm(1.0).anchoCm(1.0).altoCm(1.0)
                .tipoServicio(TipoServicio.ESTANDAR).prioridadSugerida(Prioridad.MEDIA).estado(estado)
                .fechaCreacion(LocalDateTime.ofInstant(ahora.minusSeconds(200000),ZoneOffset.UTC)).build());
    }
    EventoUbicacionPedido ubicacion(Pedido p,EtapaUbicacion etapa,Instant fecha) {
        return ubicaciones.saveAndFlush(new EventoUbicacionPedido(UUID.randomUUID().toString(),p.getId(),p.getNumeroTracking(),etapa,
                etapa.estadoPedido(),ciudad,"Ciudad sintética",oficina,"Oficina sintética",null,null,null,null,
                operadorUsuario,OrigenUbicacion.SELECTOR,LocalDateTime.ofInstant(fecha,ZoneOffset.UTC),null));
    }
    void evaluar(Pedido p) { motor.evaluar(p.getId()); }
    long cantidad(Pedido p) { return jdbc.queryForObject("SELECT count(*) FROM anomalias WHERE envio_id=?",Long.class,p.getId()); }
    long activas(Pedido p) { return jdbc.queryForObject("SELECT count(*) FROM anomalias WHERE envio_id=? AND estado='ACTIVA'",Long.class,p.getId()); }

    @Test
    void permanenciaLimiteExactoDuplicadosYCierre() {
        var p=pedido(EstadoPedido.RECIBIDO_EN_ORIGEN);
        ubicacion(p,EtapaUbicacion.EN_BODEGA_ORIGEN,ahora.minusSeconds(24*3600));
        evaluar(p); assertThat(cantidad(p)).isZero();
        when(reloj.instant()).thenReturn(ahora.plusSeconds(1));
        evaluar(p); evaluar(p); assertThat(cantidad(p)).isEqualTo(1);
        ubicacion(p,EtapaUbicacion.EN_TRANSITO_VEHICULO,ahora);
        evaluar(p); assertThat(activas(p)).isZero();
        assertThat(jdbc.queryForObject("SELECT origen_cierre FROM anomalias WHERE envio_id=?",String.class,p.getId())).isEqualTo("SISTEMA");
    }

    @Test
    void registroRepetidoMismaOficinaNoReiniciaPermanencia() {
        var p=pedido(EstadoPedido.RECIBIDO_EN_ORIGEN);
        ubicacion(p,EtapaUbicacion.EN_BODEGA_ORIGEN,ahora.minusSeconds(25*3600));
        ubicacion(p,EtapaUbicacion.EN_BODEGA_ORIGEN,ahora.minusSeconds(60));
        evaluar(p); assertThat(activas(p)).isEqualTo(1);
    }

    @Test
    void parametrosCambianSinReinicioYSeAuditan() throws Exception {
        var p=pedido(EstadoPedido.RECIBIDO_EN_ORIGEN);
        ubicacion(p,EtapaUbicacion.EN_BODEGA_ORIGEN,ahora.minusSeconds(12*3600));
        evaluar(p); assertThat(cantidad(p)).isZero();
        mvc.perform(put("/api/v1/pedidos/anomalias/parametros").header("Authorization","Bearer "+operadorToken)
                .header("X-DBA-Key","test-dba-key").contentType("application/json")
                .content("{\"permanenciaHoras\":8,\"inactividadHoras\":4,\"recepcionHoras\":2,\"version\":0,\"origen\":\"WEB\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        evaluar(p); assertThat(activas(p)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT usuario_id FROM historial_parametros_anomalia",Long.class)).isEqualTo(operadorUsuario);
        mvc.perform(put("/api/v1/pedidos/anomalias/parametros").header("Authorization","Bearer "+operadorToken)
                .header("X-DBA-Key","test-dba-key").contentType("application/json")
                .content("{\"permanenciaHoras\":9,\"inactividadHoras\":4,\"recepcionHoras\":2,\"version\":0,\"origen\":\"WEB\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void inactividadExigeRutaIgnoraPausaYConsideraNuevosEventos() {
        var p=pedido(EstadoPedido.EN_REPARTO);
        evaluar(p); assertThat(cantidad(p)).isZero();
        Ruta ruta=Ruta.crear(conductorId,LocalDate.now()); ruta.agregarParada(p.getId()); rutas.saveAndFlush(ruta);
        jdbc.update("UPDATE paradas_ruta SET fecha_asignacion=? WHERE id_pedido=?",LocalDateTime.ofInstant(ahora.minusSeconds(4*3600),ZoneOffset.UTC),p.getId());
        evaluar(p); assertThat(cantidad(p)).isZero();
        when(reloj.instant()).thenReturn(ahora.plusSeconds(1)); evaluar(p); assertThat(activas(p)).isEqualTo(1);
        p=pedidos.findById(p.getId()).orElseThrow(); p.pausar(); pedidos.saveAndFlush(p);
        evaluar(p); assertThat(activas(p)).isZero();
    }

    @Test
    void recepcionClienteAjenoYConfirmacionConRecursoIncorrecto() throws Exception {
        var p=pedido(EstadoPedido.EN_TRANSITO);
        var llegada=ubicacion(p,EtapaUbicacion.LLEGADO_OFICINA_DESTINO,ahora.minusSeconds(2*3600));
        evaluar(p); assertThat(cantidad(p)).isZero();
        when(reloj.instant()).thenReturn(ahora.plusSeconds(1)); evaluar(p);
        String path="/api/v1/pedidos/mios/tracking/"+p.getNumeroTracking()+"/alertas";
        mvc.perform(get(path).header("Authorization","Bearer "+clienteToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].enlaceLlamada").value("tel:+9990000001"))
                .andExpect(jsonPath("$[0].tipo").doesNotExist()).andExpect(jsonPath("$[0].envioId").doesNotExist())
                .andExpect(jsonPath("$[0].eventoOrigen").doesNotExist());
        mvc.perform(get(path).header("Authorization","Bearer "+destinatarioToken)).andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization","Bearer "+ajenoToken)).andExpect(status().isForbidden());
        var otro=pedido(EstadoPedido.EN_TRANSITO);
        mvc.perform(post("/api/v1/pedidos/"+otro.getId()+"/recepcion-oficina").header("Authorization","Bearer "+operadorToken)
                .contentType("application/json").content("{\"eventoLlegadaId\":"+llegada.getId()+",\"origen\":\"WEB\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/pedidos/"+p.getId()+"/recepcion-oficina").header("Authorization","Bearer "+operadorToken)
                .contentType("application/json").content("{\"eventoLlegadaId\":"+llegada.getId()+",\"origen\":\"WEB\"}"))
                .andExpect(status().isNoContent());
        evaluar(p); assertThat(activas(p)).isZero();
        mvc.perform(get(path).header("Authorization","Bearer "+clienteToken)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void cierreNoReapareceFallbackYEstadosTerminales() throws Exception {
        var p=pedido(EstadoPedido.RECIBIDO_EN_ORIGEN);
        ubicacion(p,EtapaUbicacion.EN_BODEGA_ORIGEN,ahora.minusSeconds(25*3600)); evaluar(p);
        jdbc.update("UPDATE oficinas_logisticas SET telefono=? WHERE id=?","javascript:alert(1)",oficina);
        mvc.perform(get("/api/v1/pedidos/mios/tracking/"+p.getNumeroTracking()+"/alertas").header("Authorization","Bearer "+clienteToken))
                .andExpect(jsonPath("$[0].enlaceLlamada").value("tel:+9990000000"));
        long id=alertas.activas(p.getId()).get(0).id();
        String url="/api/v1/pedidos/anomalias/"+id+"/cierre";
        for (int i=0;i<2;i++) mvc.perform(post(url).header("Authorization","Bearer "+operadorToken).contentType("application/json")
                .content("{\"estado\":\"DESCARTADA\",\"origen\":\"WEB\"}")).andExpect(status().isOk());
        evaluar(p); assertThat(cantidad(p)).isEqualTo(1); assertThat(activas(p)).isZero();
        assertThat(alertas.obtener(id).cerradaPor()).isEqualTo(operadorUsuario);
        mvc.perform(post(url).header("Authorization","Bearer "+operadorToken).contentType("application/json")
                .content("{\"estado\":\"RESUELTA\",\"origen\":\"WEB\"}")).andExpect(status().isConflict());
    }

    @Test
    void seguridadRolesEntradasMaliciosasYLimites() throws Exception {
        for (String token : new String[]{clienteToken,conductorToken}) {
            mvc.perform(get("/api/v1/pedidos/anomalias").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/pedidos/anomalias/parametros").header("Authorization","Bearer "+operadorToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/pedidos/anomalias/parametros").header("X-DBA-Key","test-dba-key")).andExpect(status().isUnauthorized());
        for (String tipo : new String[]{"<script>","' OR 1=1 --"}) {
            mvc.perform(get("/api/v1/pedidos/anomalias").param("tipo",tipo).header("Authorization","Bearer "+operadorToken))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/pedidos/anomalias?tamano=101").header("Authorization","Bearer "+operadorToken)).andExpect(status().isBadRequest());
        for (int valor : new int[]{0,-1,169}) {
            mvc.perform(put("/api/v1/pedidos/anomalias/parametros").header("Authorization","Bearer "+operadorToken)
                    .header("X-DBA-Key","test-dba-key").contentType("application/json")
                    .content("{\"permanenciaHoras\":"+valor+",\"inactividadHoras\":4,\"recepcionHoras\":2,\"version\":0,\"origen\":\"WEB\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void rateLimitCompartidoYCamposNoDeclarados() throws Exception {
        mvc.perform(put("/api/v1/pedidos/anomalias/parametros").header("Authorization","Bearer "+operadorToken)
                .header("X-DBA-Key","test-dba-key").contentType("application/json")
                .content("{\"permanenciaHoras\":24,\"inactividadHoras\":4,\"recepcionHoras\":2,\"version\":0,\"origen\":\"WEB\",\"usuarioId\":999}"))
                .andExpect(status().isBadRequest());
        jdbc.update("DELETE FROM limite_anomalias");
        for (int i=0;i<60;i++) mvc.perform(get("/api/v1/pedidos/anomalias").header("Authorization","Bearer "+operadorToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/pedidos/anomalias").header("Authorization","Bearer "+operadorToken))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void clienteOcultaAlertasDeEnvioCanceladoAntesDelJob() throws Exception {
        var p=pedido(EstadoPedido.EN_TRANSITO);
        ubicacion(p,EtapaUbicacion.LLEGADO_OFICINA_DESTINO,ahora.minusSeconds(25*3600));
        evaluar(p); assertThat(activas(p)).isEqualTo(2);
        jdbc.update("UPDATE pedidos SET estado='CANCELADO' WHERE id_pedido=?",p.getId());
        mvc.perform(get("/api/v1/pedidos/mios/tracking/"+p.getNumeroTracking()+"/alertas")
                .header("Authorization","Bearer "+clienteToken)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        evaluar(p); assertThat(activas(p)).isZero();
    }

    @Test
    void dosEvaluacionesConcurrentesUnaSolaAlertaYUnSoloTrabajo() throws Exception {
        var p=pedido(EstadoPedido.RECIBIDO_EN_ORIGEN);
        ubicacion(p,EtapaUbicacion.EN_BODEGA_ORIGEN,ahora.minusSeconds(25*3600));
        ExecutorService ejecutor=Executors.newFixedThreadPool(2);
        try {
            var uno=ejecutor.submit(()->evaluar(p)); var dos=ejecutor.submit(()->evaluar(p));
            uno.get(15,TimeUnit.SECONDS); dos.get(15,TimeUnit.SECONDS);
        } finally { ejecutor.shutdownNow(); }
        assertThat(cantidad(p)).isEqualTo(1);
        tarea.ejecutar(); assertThat(cantidad(p)).isEqualTo(1);
    }

    @Test
    void segundoFalloEsInmediatoYNoDuplicaContador() throws Exception {
        var p=pedido(EstadoPedido.EN_REPARTO);
        String incidencia="/api/v1/pedidos/"+p.getId()+"/incidencias";
        mvc.perform(post(incidencia).header("Authorization","Bearer "+operadorToken).contentType("application/json")
                .content("{\"tipo\":\"CLIENTE_AUSENTE\"}")).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/pedidos/mios/tracking/"+p.getNumeroTracking()+"/reprogramacion")
                .header("Authorization","Bearer "+clienteToken).contentType("application/json")
                .content("{\"fecha\":\""+LocalDate.now().plusDays(1)+"\"}")).andExpect(status().isOk());
        mvc.perform(post(incidencia).header("Authorization","Bearer "+operadorToken).contentType("application/json")
                .content("{\"tipo\":\"CLIENTE_AUSENTE\"}")).andExpect(status().isCreated());
        assertThat(cantidad(p)).isEqualTo(1);
        assertThat(pedidos.findById(p.getId()).orElseThrow().getIntentosEntregaFallidos()).isEqualTo(2);
    }
}

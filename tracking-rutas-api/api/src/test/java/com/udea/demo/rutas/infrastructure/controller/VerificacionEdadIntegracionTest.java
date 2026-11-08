package com.udea.demo.rutas.infrastructure.controller;

import com.udea.demo.pedidos.domain.event.DevolucionIniciadaEvent;
import com.udea.demo.pedidos.domain.model.*;
import com.udea.demo.pedidos.interfaces.persistence.PedidoRepository;
import com.udea.demo.rutas.domain.model.Ruta;
import com.udea.demo.rutas.interfaces.persistence.RutaRepository;
import com.udea.demo.usuarios.application.service.AutenticacionService;
import com.udea.demo.usuarios.domain.model.*;
import com.udea.demo.usuarios.interfaces.persistence.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-13 CA-5/6/7: integración HTTP (H2, perfil test) del panel del conductor con verificación de edad.
 * Los triggers de PostgreSQL (V25/V27) se prueban con scripts/probar-migracion.sh; aquí se prueba la capa de
 * aplicación. Todos los datos y tokens son sintéticos y se generan en cada prueba.
 */
@SpringBootTest(properties = {"app.api.role=rutas", "app.messaging.rabbitmq.enabled=false", "spring.flyway.enabled=false"})
@ActiveProfiles("test")
@RecordApplicationEvents
@DisplayName("HU-13 Verificación de edad en el panel del conductor")
class VerificacionEdadIntegracionTest {
    private static final String BASE = "/api/v1/conductor/panel/entregas/";

    @Autowired private WebApplicationContext context;
    @Autowired private jakarta.servlet.Filter springSecurityFilterChain;
    @Autowired private ApplicationEvents eventos;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private SesionUsuarioRepository sesiones;
    @Autowired private ConductorRepository conductores;
    @Autowired private RutaRepository rutas;
    @Autowired private PedidoRepository pedidos;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mvc;
    private final List<Long> usuariosCreados = new ArrayList<>();
    private Long pedidoId;
    private Ruta ruta;
    private String tokenConductor;
    private String tokenOtroConductor;
    private String tokenOperador;
    private String tokenCliente;

    @BeforeEach
    void datos() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        Usuario conductor = usuario("cond-" + sufijo + "@hu13.invalid", Rol.CONDUCTOR);
        tokenConductor = sesion(conductor);
        Conductor perfil = conductores.saveAndFlush(Conductor.builder().usuario(conductor).licencia("LIC-" + sufijo)
                .estado("ACTIVO").build());
        Usuario otro = usuario("otro-" + sufijo + "@hu13.invalid", Rol.CONDUCTOR);
        tokenOtroConductor = sesion(otro);
        conductores.saveAndFlush(Conductor.builder().usuario(otro).licencia("LIC2-" + sufijo).estado("ACTIVO").build());
        tokenOperador = sesion(usuario("op-" + sufijo + "@hu13.invalid", Rol.OPERADOR));
        tokenCliente = sesion(usuario("cli-" + sufijo + "@hu13.invalid", Rol.CLIENTE));

        Pedido pedido = Pedido.builder().numeroPedido("HU13-" + sufijo).clienteId(10L)
                .direccionOrigen("Carrera sintetica 1").direccionDestino("Calle sintetica 2").descripcionPaquete("Caja")
                .pesoKg(1.0).largoCm(10.0).anchoCm(10.0).altoCm(10.0).tipoServicio(TipoServicio.ESTANDAR)
                .prioridadSugerida(Prioridad.MEDIA).estado(EstadoPedido.EN_REPARTO).fechaCreacion(LocalDateTime.now())
                .numeroTracking("LT13" + sufijo).build();
        ReflectionTestUtils.setField(pedido, "requiereVerificacionEdad", true);
        pedidoId = pedidos.saveAndFlush(pedido).getId();
        ruta = Ruta.crear(perfil.getId(), LocalDate.now());
        ruta.agregarParada(pedidoId);
        ruta = rutas.saveAndFlush(ruta);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM verificacion_edad_log WHERE id_pedido = ?", pedidoId);
        jdbc.update("DELETE FROM eventos_resultado_entrega WHERE id_pedido = ?", pedidoId);
        jdbc.update("DELETE FROM historial_pedidos WHERE id_pedido = ?", pedidoId);
        rutas.delete(rutas.findById(ruta.getId()).orElseThrow());
        pedidos.deleteById(pedidoId);
        for (Long id : usuariosCreados) {
            jdbc.update("DELETE FROM sesiones_usuario WHERE usuario_id = ?", id);
            jdbc.update("DELETE FROM conductores WHERE id_usuario = ?", id);
            jdbc.update("DELETE FROM usuarios WHERE id = ?", id);
        }
    }

    private Usuario usuario(String email, Rol rol) {
        Usuario u = usuarios.saveAndFlush(Usuario.builder().nombre("Sintetico " + rol).email(email).password("no-es-hash")
                .rol(rol).estado(EstadoUsuario.ACTIVO).activo(true).aceptoTerminos(false).build());
        usuariosCreados.add(u.getId());
        return u;
    }

    private String sesion(Usuario usuario) {
        String token = UUID.randomUUID() + "-" + UUID.randomUUID();
        LocalDateTime ahora = LocalDateTime.now();
        sesiones.saveAndFlush(new SesionUsuario(usuario, AutenticacionService.hash(token),
                AutenticacionService.hash(token + "-refresh"), ahora.plusMinutes(15), ahora.plusDays(7), ahora));
        return token;
    }

    private ResultActions verificar(String token, String json) throws Exception {
        var req = post(BASE + pedidoId + "/verificacion-edad").contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) req = req.header("Authorization", "Bearer " + token);
        return mvc.perform(req);
    }

    private ResultActions entregar(String token) throws Exception {
        String json = """
                {"resultado":"ENTREGADO","fechaEvento":"%s","idEventoCliente":"%s"}"""
                .formatted(OffsetDateTime.now().minusMinutes(1), UUID.randomUUID());
        return mvc.perform(post(BASE + pedidoId + "/resultado").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Integer logs() {
        return jdbc.queryForObject("SELECT count(*) FROM verificacion_edad_log WHERE id_pedido = ?", Integer.class, pedidoId);
    }

    @Test
    @DisplayName("Negativo: sin sesión responde 401 y no registra nada")
    void sinSesion() throws Exception {
        verificar(null, "{\"resultado\":\"CUMPLE\"}").andExpect(status().isUnauthorized());
        assertThat(logs()).isZero();
    }

    @Test
    @DisplayName("Negativo rol incorrecto: operador y cliente reciben 403 (solo el conductor registra la verificación)")
    void rolIncorrecto() throws Exception {
        verificar(tokenOperador, "{\"resultado\":\"CUMPLE\"}").andExpect(status().isForbidden());
        verificar(tokenCliente, "{\"resultado\":\"NO_CUMPLE\"}").andExpect(status().isForbidden());
        assertThat(logs()).isZero();
    }

    @Test
    @DisplayName("Negativo recurso ajeno: otro conductor recibe 403 y el envío no cambia")
    void conductorAjeno() throws Exception {
        verificar(tokenOtroConductor, "{\"resultado\":\"NO_CUMPLE\"}").andExpect(status().isForbidden());
        assertThat(logs()).isZero();
        assertThat(pedidos.findById(pedidoId).orElseThrow().getEstado()).isEqualTo(EstadoPedido.EN_REPARTO);
    }

    @Test
    @DisplayName("Negativo entrada maliciosa: resultados fuera de la lista blanca responden 400")
    void resultadoInvalido() throws Exception {
        for (String json : new String[]{"{\"resultado\":\"APROBADO\"}", "{\"resultado\":\"cumple\"}", "{}",
                "{\"resultado\":\"CUMPLE'; DROP TABLE pedidos;--\"}", "{\"resultado\":\"CUMPLE\",\"origen\":\"<script>\"}"}) {
            verificar(tokenConductor, json).andExpect(status().isBadRequest());
        }
        assertThat(logs()).isZero();
    }

    @Test
    @DisplayName("Negativo CA-4 (13-BE-03): el conductor no puede activar ni quitar la marca 18+ por el panel (403)")
    void conductorNoTocaLaMarca() throws Exception {
        verificar(tokenConductor, "{\"resultado\":\"CUMPLE\",\"requiereVerificacionEdad\":false}")
                .andExpect(status().isForbidden());
        String json = """
                {"resultado":"ENTREGADO","fechaEvento":"%s","idEventoCliente":"%s","requiere_verificacion_edad":false}"""
                .formatted(OffsetDateTime.now().minusMinutes(1), UUID.randomUUID());
        mvc.perform(post(BASE + pedidoId + "/resultado").header("Authorization", "Bearer " + tokenConductor)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden());
        assertThat(pedidos.findById(pedidoId).orElseThrow().isRequiereVerificacionEdad()).isTrue();
        assertThat(pedidos.findById(pedidoId).orElseThrow().getEstado()).isEqualTo(EstadoPedido.EN_REPARTO);
        assertThat(logs()).isZero();
    }

    private ResultActions sincronizarEntregado() throws Exception {
        String json = """
                {"eventos":[{"pedidoId":%d,"evento":{"resultado":"ENTREGADO","fechaEvento":"%s","idEventoCliente":"%s"}}]}"""
                .formatted(pedidoId, OffsetDateTime.now().minusMinutes(5), UUID.randomUUID());
        return mvc.perform(post(BASE + "sincronizacion").header("Authorization", "Bearer " + tokenConductor)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    @DisplayName("Negativo CA-5 offline: un ENTREGADO sincronizado sin verificación queda RECHAZADO y no se aplica")
    void offlineSinVerificacion() throws Exception {
        sincronizarEntregado().andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[0].estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.resultados[0].motivoRechazo")
                        .value("El envío requiere verificación de edad aprobada antes de marcarse como entregado"));
        assertThat(pedidos.findById(pedidoId).orElseThrow().getEstado()).isEqualTo(EstadoPedido.EN_REPARTO);

        verificar(tokenConductor, "{\"resultado\":\"CUMPLE\",\"origen\":\"OFFLINE\"}").andExpect(status().isOk());
        sincronizarEntregado().andExpect(jsonPath("$.resultados[0].estado").value("APLICADO"));
        assertThat(pedidos.findById(pedidoId).orElseThrow().getEstado()).isEqualTo(EstadoPedido.ENTREGADO);
    }

    @Test
    @DisplayName("Concurrencia: dos resultados simultáneos (CUMPLE y NO_CUMPLE) dejan un solo registro coherente")
    void verificacionesConcurrentes() throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch salida = new java.util.concurrent.CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<Integer>> respuestas = new ArrayList<>();
            for (String r : new String[]{"CUMPLE", "NO_CUMPLE"}) {
                respuestas.add(pool.submit(() -> {
                    salida.await();
                    return verificar(tokenConductor, "{\"resultado\":\"" + r + "\"}").andReturn().getResponse().getStatus();
                }));
            }
            salida.countDown();
            List<Integer> codigos = new ArrayList<>();
            for (var f : respuestas) codigos.add(f.get(30, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(codigos).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(logs()).isEqualTo(1);
        String resultado = jdbc.queryForObject("SELECT resultado FROM verificacion_edad_log WHERE id_pedido = ?",
                String.class, pedidoId);
        EstadoPedido estado = pedidos.findById(pedidoId).orElseThrow().getEstado();
        assertThat(estado).isEqualTo(resultado.equals("CUMPLE") ? EstadoPedido.EN_REPARTO
                : EstadoPedido.ENTREGA_FALLIDA_VERIFICACION_EDAD);
    }

    @Test
    @DisplayName("Auditoría: 'Mayor de edad verificado' queda con conductor, fecha UTC y origen, sin datos del documento")
    void auditoriaCumple() throws Exception {
        verificar(tokenConductor, "{\"resultado\":\"CUMPLE\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaVerificacion").exists());
        var historial = jdbc.queryForMap("SELECT id_usuario, campo_observado, detalle, fecha FROM historial_pedidos "
                + "WHERE id_pedido = ? AND tipo_evento = 'VERIFICACION_EDAD'", pedidoId);
        assertThat(historial.get("ID_USUARIO")).isNotNull();
        assertThat(historial.get("CAMPO_OBSERVADO")).isEqualTo("EN_LINEA");
        assertThat(historial.get("DETALLE")).isEqualTo("Mayor de edad verificado");
        assertThat(historial.get("FECHA")).isNotNull();
        assertThat(jdbc.queryForObject("SELECT fecha_verificacion FROM verificacion_edad_log WHERE id_pedido = ?",
                java.time.OffsetDateTime.class, pedidoId)).isNotNull();
    }

    @Test
    @DisplayName("CA-5: un envío 18+ no se puede cerrar como ENTREGADO sin verificación (409)")
    void entregadoSinVerificacion() throws Exception {
        entregar(tokenConductor).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("El envío requiere verificación de edad aprobada antes de marcarse como entregado"));
        assertThat(pedidos.findById(pedidoId).orElseThrow().getEstado()).isEqualTo(EstadoPedido.EN_REPARTO);
    }

    @Test
    @DisplayName("CA-5 detalle: el panel muestra el indicador 18+ y el resultado registrado")
    void detalleMuestraIndicador() throws Exception {
        mvc.perform(get(BASE + pedidoId).header("Authorization", "Bearer " + tokenConductor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiereVerificacionEdad").value(true))
                .andExpect(jsonPath("$.resultadoVerificacionEdad").doesNotExist());
        verificar(tokenConductor, "{\"resultado\":\"CUMPLE\"}").andExpect(status().isOk());
        mvc.perform(get(BASE + pedidoId).header("Authorization", "Bearer " + tokenConductor))
                .andExpect(jsonPath("$.resultadoVerificacionEdad").value("CUMPLE"));
    }

    @Test
    @DisplayName("CA-6: 'Mayor de edad verificado' habilita el cierre como ENTREGADO; un reintento es idempotente")
    void cumpleYEntrega() throws Exception {
        verificar(tokenConductor, "{\"resultado\":\"CUMPLE\",\"numeroDocumento\":\"1000000001\",\"imagen\":\"data:x\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeEntregarse").value(true))
                .andExpect(jsonPath("$.duplicado").value(false))
                .andExpect(jsonPath("$.numeroDocumento").doesNotExist());
        verificar(tokenConductor, "{\"resultado\":\"CUMPLE\"}").andExpect(jsonPath("$.duplicado").value(true));
        verificar(tokenConductor, "{\"resultado\":\"NO_CUMPLE\"}").andExpect(status().isConflict());
        assertThat(logs()).isEqualTo(1);

        entregar(tokenConductor).andExpect(status().isOk());
        assertThat(pedidos.findById(pedidoId).orElseThrow().getEstado()).isEqualTo(EstadoPedido.ENTREGADO);
    }

    @Test
    @DisplayName("CA-7: 'Verificación no aprobada' deja la entrega fallida por edad, cancela la parada e inicia el retorno a bodega")
    void noCumpleRetornaABodega() throws Exception {
        verificar(tokenConductor, "{\"resultado\":\"NO_CUMPLE\",\"origen\":\"OFFLINE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoPedido").value("ENTREGA_FALLIDA_VERIFICACION_EDAD"))
                .andExpect(jsonPath("$.puedeEntregarse").value(false));

        assertThat(pedidos.findById(pedidoId).orElseThrow().getEstado())
                .isEqualTo(EstadoPedido.ENTREGA_FALLIDA_VERIFICACION_EDAD);
        assertThat(jdbc.queryForObject("SELECT estado FROM paradas_ruta WHERE id_pedido = ?", String.class, pedidoId))
                .isEqualTo("CANCELADA");
        assertThat(eventos.stream(DevolucionIniciadaEvent.class))
                .anyMatch(e -> e.pedidoId().equals(pedidoId) && e.motivo() == MotivoDevolucion.VERIFICACION_EDAD_NO_APROBADA);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM historial_pedidos WHERE id_pedido = ? AND tipo_evento = "
                + "'VERIFICACION_EDAD' AND campo_observado = 'OFFLINE'", Integer.class, pedidoId)).isEqualTo(1);
        // Ya no se puede entregar.
        entregar(tokenConductor).andExpect(status().is4xxClientError());
    }
}

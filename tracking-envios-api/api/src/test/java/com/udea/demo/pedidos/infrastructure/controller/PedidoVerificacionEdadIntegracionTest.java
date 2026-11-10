package com.udea.demo.pedidos.infrastructure.controller;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-13 CA-4: solo el Cliente Remitente define "Verificación de edad obligatoria (18+)".
 * Integración HTTP (H2, perfil test): creación, corrección y validación. Los datos y tokens son sintéticos.
 */
@SpringBootTest(properties = {"app.api.role=pedidos", "app.messaging.rabbitmq.enabled=false", "spring.flyway.enabled=false"})
@ActiveProfiles("test")
@DisplayName("HU-13 Verificación de edad 18+ al crear y corregir envíos")
class PedidoVerificacionEdadIntegracionTest {
    @Autowired private WebApplicationContext context;
    @Autowired private jakarta.servlet.Filter springSecurityFilterChain;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private SesionUsuarioRepository sesiones;
    @Autowired private ClienteRepository clientes;
    @Autowired private OperadorRepository operadores;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mvc;
    private final List<Long> usuariosCreados = new ArrayList<>();
    private String tokenRemitente;
    private String tokenOtroCliente;
    private String tokenOperador;
    private String tokenConductor;

    @BeforeEach
    void datos() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        String s = UUID.randomUUID().toString().substring(0, 8);
        Usuario remitente = usuario("rem-" + s + "@hu13.invalid", Rol.CLIENTE);
        clientes.saveAndFlush(Cliente.builder().usuario(remitente).ciudad("Medellin").build());
        tokenRemitente = sesion(remitente);
        Usuario otro = usuario("otro-" + s + "@hu13.invalid", Rol.CLIENTE);
        clientes.saveAndFlush(Cliente.builder().usuario(otro).ciudad("Medellin").build());
        tokenOtroCliente = sesion(otro);
        Usuario operador = usuario("op-" + s + "@hu13.invalid", Rol.OPERADOR);
        operadores.saveAndFlush(new Operador(null, operador, "EMP-" + s));
        tokenOperador = sesion(operador);
        tokenConductor = sesion(usuario("cond-" + s + "@hu13.invalid", Rol.CONDUCTOR));
    }

    @AfterEach
    void limpiar() {
        for (Long id : usuariosCreados) {
            jdbc.update("DELETE FROM historial_pedidos WHERE id_pedido IN (SELECT p.id_pedido FROM pedidos p "
                    + "JOIN clientes c ON c.id_cliente = p.id_cliente WHERE c.id_usuario = ?)", id);
            jdbc.update("DELETE FROM pedidos WHERE id_cliente IN (SELECT id_cliente FROM clientes WHERE id_usuario = ?)", id);
            jdbc.update("DELETE FROM sesiones_usuario WHERE usuario_id = ?", id);
            jdbc.update("DELETE FROM clientes WHERE id_usuario = ?", id);
            jdbc.update("DELETE FROM operadores WHERE id_usuario = ?", id);
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

    private static String envio(String extra) {
        return """
            {"direccionOrigen":"Carrera sintetica 10","ciudadOrigen":"Medellin","codigoPostalOrigen":"050001",
             "direccionDestino":"Calle sintetica 20","ciudadDestino":"Medellin","codigoPostalDestino":"050002",
             "descripcionPaquete":"Caja sintetica","pesoKg":1.5,"largoCm":10,"anchoCm":10,"altoCm":10,
             "tipoServicio":"ESTANDAR","destinatarioNombre":"Destinatario Sintetico","destinatarioTelefono":"+573000000001",
             "remitenteTelefono":"+573000000002"%s}""".formatted(extra == null ? "" : "," + extra);
    }

    private ResultActions crear(String token, String json) throws Exception {
        var req = post("/api/v1/pedidos").contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) req = req.header("Authorization", "Bearer " + token);
        return mvc.perform(req);
    }

    private Long id(MvcResult r) throws Exception {
        String cuerpo = r.getResponse().getContentAsString();
        return Long.valueOf(cuerpo.replaceAll("(?s).*?\"id\":(\\d+).*", "$1"));
    }

    private Boolean flag(Long pedidoId) {
        return jdbc.queryForObject("SELECT requiere_verificacion_edad FROM pedidos WHERE id_pedido = ?", Boolean.class, pedidoId);
    }

    @Test
    @DisplayName("El remitente marca 18+ al crear: 201, visible en la respuesta, persistido y auditado")
    void remitenteMarca18() throws Exception {
        MvcResult r = crear(tokenRemitente, envio("\"requiereVerificacionEdad\":true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requiereVerificacionEdad").value(true)).andReturn();
        Long pedidoId = id(r);
        assertThat(flag(pedidoId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM historial_pedidos WHERE id_pedido = ? "
                + "AND tipo_evento = 'VERIFICACION_EDAD_CONFIGURADA' AND id_usuario IS NOT NULL", Integer.class, pedidoId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Sin marcar (o con la firma anterior), el envío no requiere verificación de edad")
    void porDefectoFalso() throws Exception {
        Long pedidoId = id(crear(tokenRemitente, envio(null)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.requiereVerificacionEdad").value(false)).andReturn());
        assertThat(flag(pedidoId)).isFalse();
    }

    @Test
    @DisplayName("Negativo rol incorrecto CA-4: operador y conductor no pueden crear envíos con la marca (403); sin sesión, 401")
    void otrosRolesNoMarcan() throws Exception {
        crear(tokenOperador, envio("\"requiereVerificacionEdad\":true")).andExpect(status().isForbidden());
        crear(tokenConductor, envio("\"requiereVerificacionEdad\":true")).andExpect(status().isForbidden());
        crear(null, envio("\"requiereVerificacionEdad\":true")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Negativo entrada maliciosa: un tipo inválido para la marca responde 400")
    void tipoInvalido() throws Exception {
        crear(tokenRemitente, envio("\"requiereVerificacionEdad\":\"<script>\"")).andExpect(status().isBadRequest());
        crear(tokenRemitente, envio("\"requiereVerificacionEdad\":{\"$ne\":false}")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Negativo CA-4 (13-BE-03): el operador que intenta quitar la marca al validar recibe 403 y nada cambia")
    void operadorNoCambiaAlValidar() throws Exception {
        Long pedidoId = id(crear(tokenRemitente, envio("\"requiereVerificacionEdad\":true")).andReturn());
        for (String campo : new String[]{"\"requiereVerificacionEdad\":false", "\"requiere_verificacion_edad\":false",
                "\"extra\":{\"RequiereVerificacionEdad\":false}"}) {
            mvc.perform(put("/api/v1/pedidos/" + pedidoId + "/validar").header("Authorization", "Bearer " + tokenOperador)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"aprobar\":true,\"observaciones\":\"ok\"," + campo + "}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("No tienes permiso para realizar esta acción"));
        }
        assertThat(flag(pedidoId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT fecha_validacion FROM pedidos WHERE id_pedido = ?",
                java.time.LocalDateTime.class, pedidoId)).as("tampoco se validó").isNull();
    }

    @Test
    @DisplayName("Negativo CA-4: el operador tampoco puede colar la marca por el estado logístico (403)")
    void operadorEstadoLogistico() throws Exception {
        Long pedidoId = id(crear(tokenRemitente, envio("\"requiereVerificacionEdad\":false")).andReturn());
        mvc.perform(put("/api/v1/pedidos/" + pedidoId + "/estado-logistico").header("Authorization", "Bearer " + tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"EN_TRANSITO\",\"requiereVerificacionEdad\":true}"))
                .andExpect(status().isForbidden());
        assertThat(flag(pedidoId)).isFalse();
    }

    @Test
    @DisplayName("Negativo CA-5 (13-BE-04): el operador no puede cerrar como ENTREGADO un envío 18+ por el estado logístico (409)")
    void operadorNoEntrega18() throws Exception {
        Long con18 = id(crear(tokenRemitente, envio("\"requiereVerificacionEdad\":true")).andReturn());
        Long sin18 = id(crear(tokenRemitente, envio("\"requiereVerificacionEdad\":false")).andReturn());
        jdbc.update("UPDATE pedidos SET estado = 'EN_REPARTO' WHERE id_pedido IN (?, ?)", con18, sin18);

        mvc.perform(put("/api/v1/pedidos/" + con18 + "/estado-logistico").header("Authorization", "Bearer " + tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"ENTREGADO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("El envío requiere verificación de edad aprobada antes de marcarse como entregado"));
        assertThat(jdbc.queryForObject("SELECT estado FROM pedidos WHERE id_pedido = ?", String.class, con18))
                .isEqualTo("EN_REPARTO");

        mvc.perform(put("/api/v1/pedidos/" + sin18 + "/estado-logistico").header("Authorization", "Bearer " + tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"ENTREGADO\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT estado FROM pedidos WHERE id_pedido = ?", String.class, sin18))
                .as("sin regresión para envíos normales").isEqualTo("ENTREGADO");
    }

    @Test
    @DisplayName("Negativo CA-4: el conductor no puede enviar la marca en un checkpoint (403)")
    void conductorCheckpoint() throws Exception {
        Long pedidoId = id(crear(tokenRemitente, envio("\"requiereVerificacionEdad\":false")).andReturn());
        mvc.perform(post("/api/v1/pedidos/" + pedidoId + "/checkpoints").header("Authorization", "Bearer " + tokenConductor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"etapa\":\"EN_REPARTO\",\"requiereVerificacionEdad\":true}"))
                .andExpect(status().isForbidden());
        assertThat(flag(pedidoId)).isFalse();
    }

    @Test
    @DisplayName("Negativo CA-4 destinatario: un cliente destinatario no puede corregir el envío ni colar la marca por su seguimiento (403)")
    void destinatario() throws Exception {
        Usuario destinatario = usuario("dest-" + UUID.randomUUID().toString().substring(0, 8) + "@hu13.invalid", Rol.CLIENTE);
        clientes.saveAndFlush(Cliente.builder().usuario(destinatario).ciudad("Medellin").build());
        String tokenDestinatario = sesion(destinatario);
        Long pedidoId = id(crear(tokenRemitente, envio("\"destinatarioEmail\":\"" + destinatario.getEmail()
                + "\",\"requiereVerificacionEdad\":false")).andExpect(status().isCreated()).andReturn());
        jdbc.update("UPDATE pedidos SET estado = 'EN_REPARTO', numero_tracking = ? WHERE id_pedido = ?",
                "LT13D" + pedidoId, pedidoId);

        mvc.perform(put("/api/v1/pedidos/" + pedidoId + "/corregir").header("Authorization", "Bearer " + tokenDestinatario)
                        .contentType(MediaType.APPLICATION_JSON).content(envio("\"requiereVerificacionEdad\":true")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/pedidos/mios/tracking/LT13D" + pedidoId + "/direccion")
                        .header("Authorization", "Bearer " + tokenDestinatario).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requiereVerificacionEdad\":true}"))
                .andExpect(status().isForbidden());
        assertThat(flag(pedidoId)).isFalse();
    }

    @Test
    @DisplayName("Negativo anti-enumeración: corregir un envío inexistente responde 403, igual que uno ajeno")
    void corregirInexistente() throws Exception {
        mvc.perform(put("/api/v1/pedidos/999999999/corregir").header("Authorization", "Bearer " + tokenRemitente)
                        .contentType(MediaType.APPLICATION_JSON).content(envio(null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("No tienes permiso para realizar esta acción"));
    }

    @Test
    @DisplayName("Negativo whitelist: campos no declarados (clienteId, estado, remitenteEmail...) responden 400 sin crear nada")
    void whitelistDeCampos() throws Exception {
        Integer antes = jdbc.queryForObject("SELECT count(*) FROM pedidos", Integer.class);
        for (String extra : new String[]{"\"clienteId\":1", "\"estado\":\"ENTREGADO\"",
                "\"remitenteEmail\":\"otro@ejemplo.invalid\"", "\"numeroTracking\":\"LT-FALSO\"",
                "\"prioridadConfirmada\":\"ALTA\"", "\"RequiereVerificacionEdad\":true"}) {
            crear(tokenRemitente, envio(extra))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("La solicitud contiene un valor no válido"));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pedidos", Integer.class)).isEqualTo(antes);
        Long pedidoId = id(crear(tokenRemitente, envio(null)).andReturn());
        mvc.perform(put("/api/v1/pedidos/" + pedidoId + "/validar").header("Authorization", "Bearer " + tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"aprobar\":true,\"estado\":\"ENTREGADO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Auditoría: el cambio de la marca registra usuario, fecha y origen; un origen fuera de la lista blanca = 400")
    void auditoriaOrigen() throws Exception {
        Long pedidoId = id(mvc.perform(post("/api/v1/pedidos").header("Authorization", "Bearer " + tokenRemitente)
                        .header("X-Origen", "movil").contentType(MediaType.APPLICATION_JSON)
                        .content(envio("\"requiereVerificacionEdad\":true")))
                .andExpect(status().isCreated()).andReturn());
        assertThat(jdbc.queryForObject("SELECT detalle FROM historial_pedidos WHERE id_pedido = ? AND tipo_evento = "
                + "'VERIFICACION_EDAD_CONFIGURADA'", String.class, pedidoId)).endsWith("(origen MOVIL)");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM historial_pedidos WHERE id_pedido = ? AND tipo_evento = "
                + "'VERIFICACION_EDAD_CONFIGURADA' AND id_usuario IS NOT NULL AND fecha IS NOT NULL", Integer.class, pedidoId))
                .isEqualTo(1);

        Integer antes = jdbc.queryForObject("SELECT count(*) FROM pedidos", Integer.class);
        mvc.perform(post("/api/v1/pedidos").header("Authorization", "Bearer " + tokenRemitente)
                        .header("X-Origen", "<script>").contentType(MediaType.APPLICATION_JSON)
                        .content(envio("\"requiereVerificacionEdad\":true")))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pedidos", Integer.class)).as("se revierte").isEqualTo(antes);
    }

    @Test
    @DisplayName("Corrección: el remitente puede cambiar la marca; otro cliente recibe 403 (recurso ajeno) y no la cambia")
    void correccion() throws Exception {
        Long pedidoId = id(crear(tokenRemitente, envio("\"requiereVerificacionEdad\":false")).andReturn());
        mvc.perform(put("/api/v1/pedidos/" + pedidoId + "/validar").header("Authorization", "Bearer " + tokenOperador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solicitarCorreccion\":true,\"campoObservado\":\"destinatarioNombre\","
                                + "\"observaciones\":\"Revisar destinatario\"}"))
                .andExpect(status().isOk());

        mvc.perform(put("/api/v1/pedidos/" + pedidoId + "/corregir").header("Authorization", "Bearer " + tokenOtroCliente)
                        .contentType(MediaType.APPLICATION_JSON).content(envio("\"requiereVerificacionEdad\":true")))
                .andExpect(status().isForbidden());
        assertThat(flag(pedidoId)).isFalse();

        mvc.perform(put("/api/v1/pedidos/" + pedidoId + "/corregir").header("Authorization", "Bearer " + tokenRemitente)
                        .contentType(MediaType.APPLICATION_JSON).content(envio("\"requiereVerificacionEdad\":true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiereVerificacionEdad").value(true));
        assertThat(flag(pedidoId)).isTrue();
    }
}

package com.udea.demo.rutas.infrastructure.controller;

import com.udea.demo.pedidos.application.service.CrearDevolucionService;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-13 CA-7 (13-BE-05): "estado + evento + retorno" es atómico. Si la creación del retorno a bodega falla,
 * la verificación NO_CUMPLE completa se revierte: no queda el log, ni el cambio de estado, ni la parada
 * cancelada, ni el historial. El cliente recibe un error genérico. Datos sintéticos.
 */
@SpringBootTest(properties = {"app.api.role=rutas", "app.messaging.rabbitmq.enabled=false", "spring.flyway.enabled=false"})
@ActiveProfiles("test")
@DisplayName("HU-13 Atomicidad de la entrega fallida por edad y el retorno a bodega")
class RetornoBodegaAtomicidadTest {
    @Autowired private WebApplicationContext context;
    @Autowired private jakarta.servlet.Filter springSecurityFilterChain;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private SesionUsuarioRepository sesiones;
    @Autowired private ConductorRepository conductores;
    @Autowired private RutaRepository rutas;
    @Autowired private PedidoRepository pedidos;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private CrearDevolucionService devoluciones;

    private MockMvc mvc;
    private final List<Long> usuariosCreados = new ArrayList<>();
    private Long pedidoId;
    private Ruta ruta;
    private String token;

    @BeforeEach
    void datos() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        Usuario conductor = usuarios.saveAndFlush(Usuario.builder().nombre("Cond").email("c-" + sufijo + "@hu13.invalid")
                .password("no-es-hash").rol(Rol.CONDUCTOR).estado(EstadoUsuario.ACTIVO).activo(true).aceptoTerminos(false).build());
        usuariosCreados.add(conductor.getId());
        token = UUID.randomUUID() + "-" + UUID.randomUUID();
        LocalDateTime ahora = LocalDateTime.now();
        sesiones.saveAndFlush(new SesionUsuario(conductor, AutenticacionService.hash(token),
                AutenticacionService.hash(token + "-r"), ahora.plusMinutes(15), ahora.plusDays(7), ahora));
        Conductor perfil = conductores.saveAndFlush(Conductor.builder().usuario(conductor).licencia("LIC-" + sufijo)
                .estado("ACTIVO").build());
        Pedido pedido = Pedido.builder().numeroPedido("AT13-" + sufijo).clienteId(10L)
                .direccionOrigen("Carrera sintetica 1").direccionDestino("Calle sintetica 2").descripcionPaquete("Caja")
                .pesoKg(1.0).largoCm(10.0).anchoCm(10.0).altoCm(10.0).tipoServicio(TipoServicio.ESTANDAR)
                .prioridadSugerida(Prioridad.MEDIA).estado(EstadoPedido.EN_REPARTO).fechaCreacion(ahora)
                .numeroTracking("AT13" + sufijo).build();
        ReflectionTestUtils.setField(pedido, "requiereVerificacionEdad", true);
        pedidoId = pedidos.saveAndFlush(pedido).getId();
        ruta = Ruta.crear(perfil.getId(), LocalDate.now());
        ruta.agregarParada(pedidoId);
        ruta = rutas.saveAndFlush(ruta);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM verificacion_edad_log WHERE id_pedido = ?", pedidoId);
        jdbc.update("DELETE FROM historial_pedidos WHERE id_pedido = ?", pedidoId);
        rutas.delete(rutas.findById(ruta.getId()).orElseThrow());
        pedidos.deleteById(pedidoId);
        for (Long id : usuariosCreados) {
            jdbc.update("DELETE FROM sesiones_usuario WHERE usuario_id = ?", id);
            jdbc.update("DELETE FROM conductores WHERE id_usuario = ?", id);
            jdbc.update("DELETE FROM usuarios WHERE id = ?", id);
        }
    }

    @Test
    @DisplayName("Si falla la creación del retorno, NO_CUMPLE se revierte por completo y responde un error genérico")
    void falloDelRetornoRevierteTodo() throws Exception {
        doThrow(new IllegalStateException("fallo simulado de infraestructura", new RuntimeException("detalle interno")))
                .when(devoluciones).crear(any(DevolucionIniciadaEvent.class));

        String cuerpo = mvc.perform(post("/api/v1/conductor/panel/entregas/" + pedidoId + "/verificacion-edad")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultado\":\"NO_CUMPLE\"}"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.error").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("detalle interno").doesNotContain("fallo simulado");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM verificacion_edad_log WHERE id_pedido = ?",
                Integer.class, pedidoId)).as("sin log").isZero();
        assertThat(jdbc.queryForObject("SELECT estado FROM pedidos WHERE id_pedido = ?", String.class, pedidoId))
                .as("sin cambio de estado").isEqualTo("EN_REPARTO");
        assertThat(jdbc.queryForObject("SELECT estado FROM paradas_ruta WHERE id_pedido = ?", String.class, pedidoId))
                .as("parada intacta").isEqualTo("PENDIENTE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM historial_pedidos WHERE id_pedido = ?",
                Integer.class, pedidoId)).as("sin historial").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pedidos WHERE id_pedido_origen_devolucion = ?",
                Integer.class, pedidoId)).as("sin retorno").isZero();
    }
}

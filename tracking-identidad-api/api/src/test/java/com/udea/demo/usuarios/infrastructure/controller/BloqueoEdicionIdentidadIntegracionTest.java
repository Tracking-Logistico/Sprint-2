package com.udea.demo.usuarios.infrastructure.controller;

import com.udea.demo.usuarios.application.service.AutenticacionService;
import com.udea.demo.usuarios.application.service.ProteccionDatosIdentidadService;
import com.udea.demo.usuarios.domain.model.*;
import com.udea.demo.usuarios.interfaces.persistence.ClienteRepository;
import com.udea.demo.usuarios.interfaces.persistence.SesionUsuarioRepository;
import com.udea.demo.usuarios.interfaces.persistence.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-13 13-BE-02 (CA-2, CA-3): la edad se calcula en el servidor y la cédula y la fecha no se modifican por ningún
 * endpoint de edición, incluso enviándolas por API directa (asignación masiva). Integración HTTP sobre H2.
 * Clave, tokens y datos sintéticos.
 */
@SpringBootTest(properties = {"app.api.role=usuarios", "app.messaging.rabbitmq.enabled=false", "spring.flyway.enabled=false"})
@ActiveProfiles("test")
@DisplayName("HU-13 Bloqueo de edición de identidad y edad calculada en el servidor")
class BloqueoEdicionIdentidadIntegracionTest {
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final String PERFIL = "/api/v1/clientes/me/perfil";

    @DynamicPropertySource
    static void clave(DynamicPropertyRegistry registro) {
        String clave = Base64.getEncoder().encodeToString(ALEATORIO.generateSeed(32));
        registro.add("app.identidad.cifrado.clave-maestra", () -> clave);
    }

    @Autowired private WebApplicationContext context;
    @Autowired private jakarta.servlet.Filter springSecurityFilterChain;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ClienteRepository clientes;
    @Autowired private SesionUsuarioRepository sesiones;
    @Autowired private ProteccionDatosIdentidadService proteccion;
    @Autowired private JdbcTemplate jdbc;

    private MockMvc mvc;
    private final List<Long> creados = new ArrayList<>();
    private Usuario cliente;
    private String token;
    private String hashOriginal;
    private String cifradoFechaOriginal;
    private static final LocalDate NACIMIENTO = LocalDate.of(1990, 5, 17);

    @BeforeEach
    void datos() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        cliente = usuario(Rol.CLIENTE, "Cliente Original");
        cliente.registrarDatosIdentidad(proteccion.proteger(
                String.valueOf(1_000_000_000L + ALEATORIO.nextInt(999_999_999)), NACIMIENTO, proteccion.hoy()), Instant.now());
        cliente = usuarios.saveAndFlush(cliente);
        clientes.saveAndFlush(Cliente.builder().usuario(cliente).ciudad("Medellin").build());
        hashOriginal = cliente.getNumeroCedulaHash();
        cifradoFechaOriginal = cliente.getFechaNacimientoCifrada();
        token = sesion(cliente);
    }

    @AfterEach
    void limpiar() {
        for (Long id : creados) {
            jdbc.update("DELETE FROM sesiones_usuario WHERE usuario_id = ?", id);
            jdbc.update("DELETE FROM clientes WHERE id_usuario = ?", id);
            jdbc.update("DELETE FROM conductores WHERE id_usuario = ?", id);
            jdbc.update("DELETE FROM operadores WHERE id_usuario = ?", id);
            jdbc.update("DELETE FROM usuarios WHERE id = ?", id);
        }
    }

    private Usuario usuario(Rol rol, String nombre) {
        Usuario u = usuarios.saveAndFlush(Usuario.builder().nombre(nombre).email(UUID.randomUUID() + "@hu13.invalid")
                .password("no-es-hash").rol(rol).estado(EstadoUsuario.ACTIVO).activo(true).aceptoTerminos(false).build());
        creados.add(u.getId());
        return u;
    }

    private String sesion(Usuario usuario) {
        String t = UUID.randomUUID() + "-" + UUID.randomUUID();
        LocalDateTime ahora = LocalDateTime.now();
        sesiones.saveAndFlush(new SesionUsuario(usuario, AutenticacionService.hash(t),
                AutenticacionService.hash(t + "-refresh"), ahora.plusMinutes(15), ahora.plusDays(7), ahora));
        return t;
    }

    private ResultActions editarPerfil(String tok, String json) throws Exception {
        return mvc.perform(put(PERFIL).header("Authorization", "Bearer " + tok)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void identidadIntacta() {
        Usuario actual = usuarios.findById(cliente.getId()).orElseThrow();
        assertThat(actual.getNumeroCedulaHash()).isEqualTo(hashOriginal);
        assertThat(actual.getFechaNacimientoCifrada()).isEqualTo(cifradoFechaOriginal);
        assertThat(actual.getNombre()).isEqualTo("Cliente Original");
    }

    @Test
    @DisplayName("La edición legítima del perfil sigue funcionando (sin regresión)")
    void edicionLegitima() throws Exception {
        editarPerfil(token, "{\"nombre\":\"Cliente Editado\",\"telefono\":\"+573000000000\",\"direccion\":\"Calle 1\"}")
                .andExpect(status().isOk());
        assertThat(usuarios.findById(cliente.getId()).orElseThrow().getNombre()).isEqualTo("Cliente Editado");
        assertThat(usuarios.findById(cliente.getId()).orElseThrow().getNumeroCedulaHash()).isEqualTo(hashOriginal);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\"numeroCedula\":\"1000000099\"",
            "\"fechaNacimiento\":\"2015-01-01\"",
            "\"fecha_nacimiento\":\"2015-01-01\"",
            "\"NumeroCedulaHash\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"",
            "\"datosIdentidadBloqueados\":false",
            "\"perfil\":{\"cedula\":\"1000000099\"}"
    })
    @DisplayName("Negativo CA-3 asignación masiva: identidad en la edición del perfil = 409 IDENTIDAD_INMUTABLE y nada cambia")
    void asignacionMasivaRechazada(String campo) throws Exception {
        String cuerpo = editarPerfil(token, "{\"nombre\":\"Intento Masivo\",\"telefono\":\"\",\"direccion\":\"Calle 1\","
                        + campo + "}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDENTIDAD_INMUTABLE"))
                .andExpect(jsonPath("$.error").value("La cédula y la fecha de nacimiento no se pueden modificar desde el perfil"))
                .andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("1000000099").doesNotContain("2015");
        identidadIntacta();
    }

    @Test
    @DisplayName("CA-2: una 'edad' enviada por el cliente se ignora; la edad la calcula el servidor")
    void edadDelClienteIgnorada() throws Exception {
        editarPerfil(token, "{\"nombre\":\"Cliente Original\",\"telefono\":\"\",\"direccion\":\"Calle 1\",\"edad\":99}")
                .andExpect(status().isOk());
        int esperada = ProteccionDatosIdentidadService.edadEn(NACIMIENTO, proteccion.hoy());
        mvc.perform(get("/api/v1/clientes/me/identidad").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edad").value(esperada))
                .andExpect(jsonPath("$.editable").value(false));
    }

    @Test
    @DisplayName("Negativo CA-3: completar de nuevo por el endpoint de identidad responde 409 y no cambia nada")
    void identidadNoSeReemplaza() throws Exception {
        mvc.perform(put("/api/v1/clientes/me/identidad").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroCedula\":\"1000000099\",\"fechaNacimiento\":\"2015-01-01\",\"edad\":30}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDENTIDAD_INMUTABLE"));
        identidadIntacta();
    }

    @Test
    @DisplayName("Negativo recurso ajeno: editar el perfil de otro cliente por id responde 403 (con o sin campos de identidad)")
    void perfilAjeno() throws Exception {
        Usuario otro = usuario(Rol.CLIENTE, "Otro Cliente");
        clientes.saveAndFlush(Cliente.builder().usuario(otro).ciudad("Medellin").build());
        mvc.perform(put("/api/v1/clientes/" + otro.getId() + "/perfil").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Secuestrado\",\"telefono\":\"\",\"direccion\":\"Calle 1\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/clientes/" + otro.getId() + "/perfil").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Secuestrado\",\"numeroCedula\":\"1000000099\"}"))
                .andExpect(status().is4xxClientError());
        assertThat(usuarios.findById(otro.getId()).orElseThrow().getNombre()).isEqualTo("Otro Cliente");
        assertThat(usuarios.findById(otro.getId()).orElseThrow().tieneDatosIdentidad()).isFalse();
    }

    @Test
    @DisplayName("Negativo rol incorrecto: un operador no puede usar la edición de perfil de cliente (403)")
    void rolIncorrecto() throws Exception {
        String tokenOperador = sesion(usuario(Rol.OPERADOR, "Operador"));
        editarPerfil(tokenOperador, "{\"nombre\":\"X\",\"numeroCedula\":\"1000000099\"}").andExpect(status().isForbidden());
        editarPerfil(tokenOperador, "{\"nombre\":\"X\"}").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Negativo administración: el cambio de rol tampoco acepta identidad; sin la clave DBA se rechaza")
    void administracion() throws Exception {
        Usuario conductor = usuario(Rol.CONDUCTOR, "Conductor");
        mvc.perform(patch("/api/v1/admin/usuarios/" + conductor.getId() + "/rol").header("X-DBA-Key", "test-dba-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"CONDUCTOR\",\"licencia\":\"LIC-1\",\"numeroCedula\":\"1000000099\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDENTIDAD_INMUTABLE"));
        mvc.perform(patch("/api/v1/admin/usuarios/" + conductor.getId() + "/rol").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"OPERADOR\"}"))
                .andExpect(status().is4xxClientError());
        assertThat(usuarios.findById(conductor.getId()).orElseThrow().getRol()).isEqualTo(Rol.CONDUCTOR);
        assertThat(usuarios.findById(conductor.getId()).orElseThrow().tieneDatosIdentidad()).isFalse();
    }

    @Test
    @DisplayName("Negativo entrada maliciosa: JSON malformado = 400; cuerpo sobredimensionado = 413; ambos genéricos")
    void entradaMaliciosa() throws Exception {
        editarPerfil(token, "{\"nombre\":\"X\",").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("La solicitud contiene datos inválidos"));
        editarPerfil(token, "{\"nombre\":\"" + "A".repeat(20_000) + "\"}")
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.error").value("No fue posible completar la solicitud"));
        identidadIntacta();
    }
}

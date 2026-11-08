package com.udea.demo.usuarios.infrastructure.controller;

import com.udea.demo.usuarios.application.service.AutenticacionService;
import com.udea.demo.usuarios.application.service.ProteccionDatosIdentidadService;
import com.udea.demo.usuarios.domain.model.*;
import com.udea.demo.usuarios.interfaces.persistence.ClienteRepository;
import com.udea.demo.usuarios.interfaces.persistence.SesionUsuarioRepository;
import com.udea.demo.usuarios.interfaces.persistence.UsuarioRepository;
import com.udea.demo.usuarios.interfaces.services.EmailServiceI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-13: GET/PUT /api/v1/clientes/me/identidad (H2, perfil test). Completar un perfil incompleto (V26), solo lectura
 * después (CA-3), edad calculada en el servidor (CA-2) y cédula enmascarada (CA-8). Clave y datos sintéticos.
 */
@SpringBootTest(properties = {"app.api.role=usuarios", "app.messaging.rabbitmq.enabled=false",
        "spring.flyway.enabled=false", "app.identidad.completar-max-intentos-hora=3"})
@ActiveProfiles("test")
@DisplayName("HU-13 Identidad del perfil del cliente")
class IdentidadPerfilIntegracionTest {
    private static final String URL = "/api/v1/clientes/me/identidad";
    private static final SecureRandom ALEATORIO = new SecureRandom();

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
    @MockitoBean private EmailServiceI email;

    private MockMvc mvc;
    private final List<Long> creados = new ArrayList<>();
    private Usuario cliente;
    private String tokenCliente;

    @BeforeEach
    void datos() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        cliente = usuario(Rol.CLIENTE);
        clientes.saveAndFlush(Cliente.builder().usuario(cliente).ciudad("Medellin").build());
        tokenCliente = sesion(cliente);
    }

    @AfterEach
    void limpiar() {
        for (Long id : creados) {
            jdbc.update("DELETE FROM sesiones_usuario WHERE usuario_id = ?", id);
            jdbc.update("DELETE FROM clientes WHERE id_usuario = ?", id);
            jdbc.update("DELETE FROM usuarios WHERE id = ?", id);
        }
    }

    private Usuario usuario(Rol rol) {
        Usuario u = usuarios.saveAndFlush(Usuario.builder().nombre("Sintetico").email(UUID.randomUUID() + "@hu13.invalid")
                .password("no-es-hash").rol(rol).estado(EstadoUsuario.ACTIVO).activo(true).aceptoTerminos(false).build());
        creados.add(u.getId());
        return u;
    }

    private String sesion(Usuario usuario) {
        String token = UUID.randomUUID() + "-" + UUID.randomUUID();
        LocalDateTime ahora = LocalDateTime.now();
        sesiones.saveAndFlush(new SesionUsuario(usuario, AutenticacionService.hash(token),
                AutenticacionService.hash(token + "-refresh"), ahora.plusMinutes(15), ahora.plusDays(7), ahora));
        return token;
    }

    private static String cedulaSintetica() {
        return String.valueOf(1_000_000_000L + ALEATORIO.nextInt(999_999_999));
    }

    private ResultActions completar(String token, String cedula, String fecha) throws Exception {
        return completarJson(token, "{\"numeroCedula\":\"" + cedula + "\",\"fechaNacimiento\":\"" + fecha + "\"}", null);
    }

    private ResultActions completarJson(String token, String json, String origen) throws Exception {
        var req = put(URL).contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) req = req.header("Authorization", "Bearer " + token);
        if (origen != null) req = req.header("X-Origen", origen);
        return mvc.perform(req);
    }

    @Test
    @DisplayName("Un perfil incompleto se consulta como pendiente y editable")
    void consultaPendiente() throws Exception {
        mvc.perform(get(URL).header("Authorization", "Bearer " + tokenCliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrada").value(false))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.numeroCedulaEnmascarado").doesNotExist());
    }

    @Test
    @DisplayName("Completar: 200 con la cédula enmascarada, la fecha, la edad calculada en el servidor y el perfil bloqueado")
    void completaYBloquea() throws Exception {
        String cedula = cedulaSintetica();
        LocalDate fecha = LocalDate.of(1990, 5, 17);
        int edadEsperada = Period.between(fecha, LocalDate.now(ZoneId.of("America/Bogota"))).getYears();

        String cuerpo = completarJson(tokenCliente, "{\"numeroCedula\":\"" + cedula + "\",\"fechaNacimiento\":\"1990-05-17\","
                        + "\"edad\":5}", "MOVIL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrada").value(true))
                .andExpect(jsonPath("$.editable").value(false))
                .andExpect(jsonPath("$.numeroCedulaEnmascarado").value("******" + cedula.substring(cedula.length() - 4)))
                .andExpect(jsonPath("$.fechaNacimiento").value("1990-05-17"))
                .andExpect(jsonPath("$.edad").value(edadEsperada))
                .andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain(cedula);
        Usuario guardado = usuarios.findById(cliente.getId()).orElseThrow();
        assertThat(guardado.isDatosIdentidadBloqueados()).isTrue();
        assertThat(guardado.getNumeroCedulaHash()).isEqualTo(proteccion.indiceCiego(cedula));

        mvc.perform(get(URL).header("Authorization", "Bearer " + tokenCliente))
                .andExpect(jsonPath("$.edad").value(edadEsperada))
                .andExpect(jsonPath("$.numeroCedulaEnmascarado").value("******" + cedula.substring(cedula.length() - 4)));
    }

    @Test
    @DisplayName("Negativo CA-3: una vez registrada, cualquier intento de modificación responde 409 IDENTIDAD_INMUTABLE")
    void inmutable() throws Exception {
        String cedula = cedulaSintetica();
        completar(tokenCliente, cedula, "1990-05-17").andExpect(status().isOk());
        completar(tokenCliente, cedulaSintetica(), "1985-01-01")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDENTIDAD_INMUTABLE"));
        assertThat(usuarios.findById(cliente.getId()).orElseThrow().getNumeroCedulaHash())
                .isEqualTo(proteccion.indiceCiego(cedula));
    }

    @Test
    @DisplayName("Negativo anti-enumeración: la cédula de otra cuenta da un 409 genérico y avisa al titular")
    void cedulaDeOtraCuenta() throws Exception {
        String cedula = cedulaSintetica();
        Usuario titular = usuario(Rol.CLIENTE);
        titular.registrarDatosIdentidad(proteccion.proteger(cedula, LocalDate.of(1980, 1, 1), LocalDate.now()), Instant.now());
        usuarios.saveAndFlush(titular);

        String cuerpo = completar(tokenCliente, cedula, "1990-05-17")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("No fue posible crear el usuario con los datos de identidad suministrados"))
                .andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain(cedula).doesNotContain(titular.getEmail());
        assertThat(usuarios.findById(cliente.getId()).orElseThrow().tieneDatosIdentidad()).isFalse();
        verify(email).enviarNotificacionCambioEstado(eq(titular.getEmail()), anyString(), anyString());
    }

    @Test
    @DisplayName("Negativo CA-1: fecha irreal, futura o cédula maliciosa responden 400 por campo sin reflejar la entrada")
    void entradasInvalidas() throws Exception {
        completar(tokenCliente, cedulaSintetica(), "2023-02-30").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fechaNacimiento").value("La fecha de nacimiento no es una fecha válida"));
        completar(tokenCliente, cedulaSintetica(), LocalDate.now(ZoneId.of("America/Bogota")).plusDays(1).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fechaNacimiento").value("La fecha de nacimiento no puede ser futura"));
        String cuerpo = completar(tokenCliente, "<script>alert(1)</script>", "1990-05-17")
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).contains("numeroCedula").doesNotContain("script");
        completarJson(tokenCliente, "{}", null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.numeroCedula").exists()).andExpect(jsonPath("$.fechaNacimiento").exists());
        assertThat(usuarios.findById(cliente.getId()).orElseThrow().tieneDatosIdentidad()).isFalse();
    }

    @Test
    @DisplayName("Negativo: un origen fuera de la lista blanca (WEB, MOVIL) responde 400")
    void origenInvalido() throws Exception {
        completarJson(tokenCliente, "{\"numeroCedula\":\"" + cedulaSintetica() + "\",\"fechaNacimiento\":\"1990-05-17\"}",
                "QR'; DROP TABLE usuarios;--").andExpect(status().isBadRequest());
        assertThat(usuarios.findById(cliente.getId()).orElseThrow().tieneDatosIdentidad()).isFalse();
    }

    @Test
    @DisplayName("Negativo rol incorrecto: operador y conductor reciben 403; sin sesión, 401")
    void rolIncorrecto() throws Exception {
        String tokenOperador = sesion(usuario(Rol.OPERADOR));
        String tokenConductor = sesion(usuario(Rol.CONDUCTOR));
        completar(tokenOperador, cedulaSintetica(), "1990-05-17").andExpect(status().isForbidden());
        completar(tokenConductor, cedulaSintetica(), "1990-05-17").andExpect(status().isForbidden());
        mvc.perform(get(URL).header("Authorization", "Bearer " + tokenOperador)).andExpect(status().isForbidden());
        completar(null, cedulaSintetica(), "1990-05-17").andExpect(status().isUnauthorized());
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Negativo anti-IDOR: no existe ruta con id; un id en la URL no permite operar sobre otra cuenta")
    void sinIdEnLaRuta() throws Exception {
        Usuario otro = usuario(Rol.CLIENTE);
        mvc.perform(put("/api/v1/clientes/" + otro.getId() + "/identidad").header("Authorization", "Bearer " + tokenCliente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numeroCedula\":\"" + cedulaSintetica() + "\",\"fechaNacimiento\":\"1990-05-17\"}"))
                .andExpect(status().isForbidden());
        assertThat(usuarios.findById(otro.getId()).orElseThrow().tieneDatosIdentidad()).isFalse();
    }

    @Test
    @DisplayName("Rate limit anti-enumeración: superado el límite de intentos por hora responde 429")
    void limiteDeIntentos() throws Exception {
        for (int i = 0; i < 3; i++) {
            completar(tokenCliente, cedulaSintetica(), "2023-02-30").andExpect(status().isBadRequest());
        }
        completar(tokenCliente, cedulaSintetica(), "1990-05-17").andExpect(status().isTooManyRequests());
        assertThat(usuarios.findById(cliente.getId()).orElseThrow().tieneDatosIdentidad()).isFalse();
    }
}

package com.udea.demo.usuarios.infrastructure.controller;

import com.udea.demo.usuarios.application.dto.RegistroClienteResponseDTO;
import com.udea.demo.usuarios.application.service.ProteccionDatosIdentidadService;
import com.udea.demo.usuarios.interfaces.services.EmailServiceI;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import com.udea.demo.config.LoginRateLimitFilter;
import org.junit.jupiter.api.BeforeEach;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-13 13-BE-01: integración HTTP de POST /api/v1/clientes/registro (H2, perfil test).
 * Prueba de punta a punta la validación por campo, la anti-enumeración de la cédula, la asignación masiva
 * y el rate limit. La clave de cifrado es aleatoria y todos los datos son sintéticos.
 */
@SpringBootTest(properties = {"app.api.role=usuarios", "app.messaging.rabbitmq.enabled=false",
        "spring.flyway.enabled=false", "app.auth.registro-rate-limit-per-minute=10"})
@ActiveProfiles("test")
@DisplayName("HU-13 Registro de cliente con cédula y fecha de nacimiento (HU-01A)")
class RegistroIdentidadIntegracionTest {
    private static final String URL = "/api/v1/clientes/registro";
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final AtomicInteger IP = new AtomicInteger(1);

    @DynamicPropertySource
    static void clave(DynamicPropertyRegistry registro) {
        String clave = Base64.getEncoder().encodeToString(ALEATORIO.generateSeed(32));
        registro.add("app.identidad.cifrado.clave-maestra", () -> clave);
    }

    @Autowired private WebApplicationContext context;
    @Autowired private jakarta.servlet.Filter springSecurityFilterChain;
    @Autowired private LoginRateLimitFilter rateLimit;
    private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ProteccionDatosIdentidadService proteccion;
    @MockitoBean private EmailServiceI email;

    @BeforeEach
    void iniciar() {
        // Mismo orden que en producción: rate limit y luego la cadena de seguridad (patrón de CheckpointIntegracionTest).
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(rateLimit, springSecurityFilterChain).build();
    }

    /** Cada prueba usa su propia IP para que el rate limit de una no afecte a otra. */
    private static RequestPostProcessor desdeIpPropia() {
        String ip = "10.13.0." + IP.getAndIncrement();
        return req -> { req.setRemoteAddr(ip); return req; };
    }

    private static String cedulaSintetica() {
        return String.valueOf(1_000_000_000L + ALEATORIO.nextInt(999_999_999));
    }

    private static String correoSintetico() {
        return UUID.randomUUID() + "@ejemplo.invalid";
    }

    private static String cuerpo(String email, String cedula, String fecha, String extra) {
        return """
            {"nombre":"Cliente Sintetico","email":"%s","password":"Clave-Segura1","confirmarPassword":"Clave-Segura1",
             "telefono":"+573000000000","direccion":"Calle sintetica 1","ciudad":"Medellin",
             "aceptoTerminos":true,"versionTerminos":"T-1","aceptoPoliticaDatos":true,"versionPoliticaDatos":"P-1"
             %s %s %s}""".formatted(email,
                cedula == null ? "" : ",\"numeroCedula\":\"" + cedula + "\"",
                fecha == null ? "" : ",\"fechaNacimiento\":\"" + fecha + "\"",
                extra == null ? "" : "," + extra);
    }

    private MvcResult registrar(RequestPostProcessor ip, String json) throws Exception {
        return mvc.perform(post(URL).with(ip).contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();
    }

    private Integer usuariosConCedula(String cedula) {
        return jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE numero_cedula_hash = ?", Integer.class,
                proteccion.indiceCiego(cedula));
    }

    @Test
    @DisplayName("Registro válido: 201 con mensaje neutro, sin datos de la cuenta, e identidad cifrada y bloqueada en la BD")
    void registroValido() throws Exception {
        String correo = correoSintetico();
        String cedula = cedulaSintetica();

        mvc.perform(post(URL).with(desdeIpPropia()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(correo, cedula, "1990-05-17", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value(RegistroClienteResponseDTO.NEUTRA.mensaje()))
                .andExpect(jsonPath("$.idCliente").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());

        var fila = jdbc.queryForMap("SELECT rol, datos_identidad_bloqueados, numero_cedula_cifrado, "
                + "fecha_nacimiento_cifrada FROM usuarios WHERE email = ?", correo);
        assertThat(fila.get("ROL")).isEqualTo("CLIENTE");
        assertThat(fila.get("DATOS_IDENTIDAD_BLOQUEADOS")).isEqualTo(true);
        assertThat(fila.get("NUMERO_CEDULA_CIFRADO").toString()).startsWith("v1:").doesNotContain(cedula);
        assertThat(fila.get("FECHA_NACIMIENTO_CIFRADA").toString()).doesNotContain("1990");
        verify(email).enviarVerificacion(eq(correo), anyString(), anyString());
    }

    @Test
    @DisplayName("Negativo anti-enumeración: la cédula de otra cuenta recibe exactamente la misma respuesta y no crea nada")
    void cedulaDeOtraCuentaRespuestaIdentica() throws Exception {
        String cedula = cedulaSintetica();
        String titular = correoSintetico();
        MvcResult primero = registrar(desdeIpPropia(), cuerpo(titular, cedula, "1990-05-17", null));

        String intruso = correoSintetico();
        String cedulaConPuntos = cedula.substring(0, 1) + "." + cedula.substring(1, 4) + "." + cedula.substring(4, 7)
                + "." + cedula.substring(7);
        MvcResult segundo = registrar(desdeIpPropia(), cuerpo(intruso, cedulaConPuntos, "2001-01-01", null));

        assertThat(segundo.getResponse().getStatus()).isEqualTo(primero.getResponse().getStatus()).isEqualTo(201);
        assertThat(segundo.getResponse().getContentAsString()).isEqualTo(primero.getResponse().getContentAsString());
        assertThat(segundo.getResponse().getContentAsString()).doesNotContain(cedula).doesNotContain(titular);
        assertThat(usuariosConCedula(cedula)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE email = ?", Integer.class, intruso)).isZero();
        verify(email, never()).enviarVerificacion(eq(intruso), anyString(), anyString());
        verify(email).enviarNotificacionCambioEstado(eq(titular), anyString(), anyString());
    }

    @Test
    @DisplayName("Negativo CA-1: sin cédula ni fecha responde 400 con un mensaje para cada campo")
    void camposObligatorios() throws Exception {
        mvc.perform(post(URL).with(desdeIpPropia()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(correoSintetico(), null, null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.numeroCedula").value("El número de cédula es obligatorio"))
                .andExpect(jsonPath("$.fechaNacimiento").value("La fecha de nacimiento es obligatoria"));
    }

    @Test
    @DisplayName("Negativo CA-1: una fecha que no existe (30 de febrero) responde 400 en fechaNacimiento")
    void fechaInexistente() throws Exception {
        mvc.perform(post(URL).with(desdeIpPropia()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(correoSintetico(), cedulaSintetica(), "2023-02-30", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fechaNacimiento").value("La fecha de nacimiento no es una fecha válida"));
    }

    @Test
    @DisplayName("Negativo CA-1: una fecha futura responde 400 en fechaNacimiento")
    void fechaFutura() throws Exception {
        String manana = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(1).toString();
        mvc.perform(post(URL).with(desdeIpPropia()).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(correoSintetico(), cedulaSintetica(), manana, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fechaNacimiento").value("La fecha de nacimiento no puede ser futura"));
    }

    @Test
    @DisplayName("Negativo entrada maliciosa: una cédula con XSS o SQL responde 400 sin reflejar la entrada ni crear la cuenta")
    void cedulaMaliciosa() throws Exception {
        String correo = correoSintetico();
        for (String maliciosa : new String[]{"<script>alert(1)</script>", "1' OR '1'='1"}) {
            MvcResult r = registrar(desdeIpPropia(), cuerpo(correo, maliciosa.replace("\"", "\\\""), "1990-05-17", null));
            assertThat(r.getResponse().getStatus()).isEqualTo(400);
            assertThat(r.getResponse().getContentAsString()).contains("numeroCedula")
                    .doesNotContain("script").doesNotContain("OR '1'");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE email = ?", Integer.class, correo)).isZero();
    }

    @Test
    @DisplayName("Negativo asignación masiva: rol, hash, edad o bloqueo enviados por el cliente se ignoran")
    void asignacionMasivaIgnorada() throws Exception {
        String correo = correoSintetico();
        String cedula = cedulaSintetica();
        String extra = "\"rol\":\"OPERADOR\",\"edad\":99,\"numeroCedulaHash\":\"" + "a".repeat(64)
                + "\",\"datosIdentidadBloqueados\":false,\"estado\":\"ACTIVO\"";

        assertThat(registrar(desdeIpPropia(), cuerpo(correo, cedula, "2010-01-01", extra)).getResponse().getStatus())
                .isEqualTo(201);

        var fila = jdbc.queryForMap("SELECT rol, estado, numero_cedula_hash, datos_identidad_bloqueados "
                + "FROM usuarios WHERE email = ?", correo);
        assertThat(fila.get("ROL")).isEqualTo("CLIENTE");
        assertThat(fila.get("ESTADO")).isEqualTo("PENDIENTE_VERIFICACION");
        assertThat(fila.get("NUMERO_CEDULA_HASH")).isEqualTo(proteccion.indiceCiego(cedula));
        assertThat(fila.get("DATOS_IDENTIDAD_BLOQUEADOS")).isEqualTo(true);
    }

    @Test
    @DisplayName("Rate limit: a partir de la solicitud 11 por minuto desde una IP responde 429 sin procesar el cuerpo")
    void rateLimitDeRegistro() throws Exception {
        RequestPostProcessor ip = desdeIpPropia();
        for (int i = 0; i < 10; i++) {
            assertThat(registrar(ip, cuerpo(correoSintetico(), null, null, null)).getResponse().getStatus()).isEqualTo(400);
        }
        MvcResult bloqueada = registrar(ip, cuerpo(correoSintetico(), cedulaSintetica(), "1990-05-17", null));
        assertThat(bloqueada.getResponse().getStatus()).isEqualTo(429);
        assertThat(registrar(desdeIpPropia(), cuerpo(correoSintetico(), null, null, null)).getResponse().getStatus())
                .as("otra IP no queda afectada").isEqualTo(400);
    }
}

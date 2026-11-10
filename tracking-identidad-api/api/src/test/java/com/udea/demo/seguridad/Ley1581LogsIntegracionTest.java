package com.udea.demo.seguridad;

import com.udea.demo.usuarios.application.service.ProteccionDatosIdentidadService;
import com.udea.demo.usuarios.interfaces.persistence.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * HU-13 CA-8 / Ley 1581: con la configuración real de logs (logback-spring.xml), ni la cédula ni la fecha de
 * nacimiento ni el correo de un login fallido aparecen en claro en la salida de la aplicación. Datos sintéticos.
 */
@SpringBootTest(properties = {"app.api.role=usuarios", "app.messaging.rabbitmq.enabled=false", "spring.flyway.enabled=false",
        "logging.level.com.udea.demo=DEBUG"})
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("HU-13 Ley 1581: cédula y datos personales fuera de los logs")
class Ley1581LogsIntegracionTest {
    private static final SecureRandom ALEATORIO = new SecureRandom();

    @DynamicPropertySource
    static void clave(DynamicPropertyRegistry registro) {
        String clave = Base64.getEncoder().encodeToString(ALEATORIO.generateSeed(32));
        registro.add("app.identidad.cifrado.clave-maestra", () -> clave);
    }

    @Autowired private WebApplicationContext context;
    @Autowired private jakarta.servlet.Filter springSecurityFilterChain;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ProteccionDatosIdentidadService proteccion;

    @Test
    @DisplayName("Registro, cédula duplicada, login fallido y asignación masiva no dejan la cédula ni el correo en los logs")
    void flujosSinDatosEnClaro(CapturedOutput salida) throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        String cedula = String.valueOf(1_000_000_000L + ALEATORIO.nextInt(999_999_999));
        String correo = "ley1581-" + UUID.randomUUID() + "@ejemplo.invalid";
        String cuerpo = """
            {"nombre":"Cliente Sintetico","email":"%s","password":"Clave-Segura1","confirmarPassword":"Clave-Segura1",
             "aceptoTerminos":true,"versionTerminos":"T-1","aceptoPoliticaDatos":true,"versionPoliticaDatos":"P-1",
             "numeroCedula":"%s","fechaNacimiento":"1990-05-17"}""";
        mvc.perform(post("/api/v1/clientes/registro").with(r -> { r.setRemoteAddr("10.81.0.1"); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo.formatted(correo, cedula)));
        // Misma cédula desde otra cuenta: aviso al titular (sin revelar nada).
        mvc.perform(post("/api/v1/clientes/registro").with(r -> { r.setRemoteAddr("10.81.0.2"); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo.formatted("otro-" + correo, cedula)));
        // Cédula maliciosa o mal formada.
        mvc.perform(post("/api/v1/clientes/registro").with(r -> { r.setRemoteAddr("10.81.0.3"); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo.formatted("x-" + correo, "<b>" + cedula + "</b>")));
        // Login fallido con el correo.
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + correo + "\",\"password\":\"incorrecta-1A!\"}"));
        // Asignación masiva de identidad en el perfil (sin sesión: igual se rechaza).
        mvc.perform(put("/api/v1/clientes/me/perfil").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"x\",\"numeroCedula\":\"" + cedula + "\"}"));

        String hash = proteccion.indiceCiego(cedula);
        String cifrado = usuarios.findByEmail(correo).orElseThrow().getNumeroCedulaCifrado();
        assertThat(salida.getAll())
                .doesNotContain(cedula)
                .doesNotContain(hash)
                .doesNotContain(cifrado)
                .doesNotContain("1990-05-17")
                .doesNotContain(correo);
    }

    @Test
    @DisplayName("Red de seguridad: aunque un componente registre una cédula por error, la salida la muestra como ****1234")
    void logAccidentalEnmascarado(CapturedOutput salida) {
        LoggerFactory.getLogger("com.udea.demo.prueba").warn("Dato recibido por error: {} y {}", "1000000077", "1.000.000.078");
        assertThat(salida.getAll()).contains("****0077").contains("****0078")
                .doesNotContain("1000000077").doesNotContain("1.000.000.078");
    }
}

package com.udea.demo.seguridad;

import com.udea.demo.usuarios.application.service.AutenticacionService;
import com.udea.demo.usuarios.application.service.ProteccionDatosIdentidadService;
import com.udea.demo.usuarios.domain.model.*;
import com.udea.demo.usuarios.interfaces.persistence.ClienteRepository;
import com.udea.demo.usuarios.interfaces.persistence.SesionUsuarioRepository;
import com.udea.demo.usuarios.interfaces.persistence.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * HU-13 13-QA-01 (CA-1, CA-3, regla 8): la inmutabilidad y la unicidad de la cédula se mantienen con peticiones
 * simultáneas. H2 no tiene los triggers de V24, así que esto prueba la protección de la propia aplicación.
 * Datos sintéticos y clave aleatoria.
 */
@SpringBootTest(properties = {"app.api.role=usuarios", "app.messaging.rabbitmq.enabled=false", "spring.flyway.enabled=false",
        "app.identidad.completar-max-intentos-hora=50", "app.auth.registro-rate-limit-per-minute=50"})
@ActiveProfiles("test")
@DisplayName("HU-13 QA: identidad bajo concurrencia")
class IdentidadConcurrenciaQaTest {
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final int HILOS = 6;

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

    private static String cedula() {
        return String.valueOf(1_000_000_000L + ALEATORIO.nextInt(999_999_999));
    }

    private <T> List<T> enParalelo(List<Callable<T>> tareas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tareas.size());
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<T>> futuros = new ArrayList<>();
            for (Callable<T> t : tareas) futuros.add(pool.submit(() -> { salida.await(); return t.call(); }));
            salida.countDown();
            List<T> resultados = new ArrayList<>();
            for (Future<T> f : futuros) resultados.add(f.get(60, TimeUnit.SECONDS));
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("CA-3: completar la identidad en paralelo con cédulas distintas deja exactamente una, sin sobrescrituras")
    void completarEnParalelo() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        Usuario cliente = usuarios.saveAndFlush(Usuario.builder().nombre("QA").email(UUID.randomUUID() + "@qa.invalid")
                .password("no-es-hash").rol(Rol.CLIENTE).estado(EstadoUsuario.ACTIVO).activo(true).aceptoTerminos(false).build());
        clientes.saveAndFlush(Cliente.builder().usuario(cliente).ciudad("Medellin").build());
        String token = UUID.randomUUID() + "-" + UUID.randomUUID();
        LocalDateTime ahora = LocalDateTime.now();
        sesiones.saveAndFlush(new SesionUsuario(cliente, AutenticacionService.hash(token),
                AutenticacionService.hash(token + "-r"), ahora.plusMinutes(15), ahora.plusDays(7), ahora));

        List<String> cedulas = new ArrayList<>();
        List<Callable<Integer>> tareas = new ArrayList<>();
        for (int i = 0; i < HILOS; i++) {
            String c = cedula();
            cedulas.add(c);
            tareas.add(() -> mvc.perform(put("/api/v1/clientes/me/identidad").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"numeroCedula\":\"" + c + "\",\"fechaNacimiento\":\"1990-05-17\"}"))
                    .andReturn().getResponse().getStatus());
        }
        List<Integer> codigos = enParalelo(tareas);

        assertThat(codigos).filteredOn(c -> c == 200).hasSize(1);
        assertThat(codigos).filteredOn(c -> c != 200).allMatch(c -> c == 409);
        String hashFinal = usuarios.findById(cliente.getId()).orElseThrow().getNumeroCedulaHash();
        int ganador = codigos.indexOf(200);
        assertThat(hashFinal).as("la que respondió 200 es la que quedó guardada")
                .isEqualTo(proteccion.indiceCiego(cedulas.get(ganador)));
    }

    @Test
    @DisplayName("CA-1: registros simultáneos con la misma cédula crean una sola cuenta y todos reciben la misma respuesta")
    void registroSimultaneoMismaCedula() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
        String c = cedula();
        List<Callable<String>> tareas = new ArrayList<>();
        for (int i = 0; i < HILOS; i++) {
            String correo = "qa-" + UUID.randomUUID() + "@qa.invalid";
            String ip = "10.99.0." + (i + 1);
            tareas.add(() -> {
                var r = mvc.perform(post("/api/v1/clientes/registro").with(req -> { req.setRemoteAddr(ip); return req; })
                        .contentType(MediaType.APPLICATION_JSON).content("""
                            {"nombre":"QA","email":"%s","password":"Clave-Segura1","confirmarPassword":"Clave-Segura1",
                             "aceptoTerminos":true,"versionTerminos":"T","aceptoPoliticaDatos":true,"versionPoliticaDatos":"P",
                             "numeroCedula":"%s","fechaNacimiento":"1990-05-17"}""".formatted(correo, c))).andReturn();
                return r.getResponse().getStatus() + " " + r.getResponse().getContentAsString();
            });
        }
        List<String> respuestas = enParalelo(tareas);

        assertThat(respuestas).as("anti-enumeración también bajo concurrencia").allMatch(r -> r.equals(respuestas.get(0)));
        assertThat(respuestas.get(0)).startsWith("201");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE numero_cedula_hash = ?", Integer.class,
                proteccion.indiceCiego(c))).isEqualTo(1);
    }
}

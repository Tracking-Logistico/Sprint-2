package com.udea.demo.usuarios.interfaces.persistence;

import com.udea.demo.usuarios.application.service.ProteccionDatosIdentidadService;
import com.udea.demo.usuarios.domain.exception.DatosIdentidadInmutablesException;
import com.udea.demo.usuarios.domain.model.EstadoUsuario;
import com.udea.demo.usuarios.domain.model.Rol;
import com.udea.demo.usuarios.domain.model.Usuario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integración JPA sobre H2 (perfil test): mapeo de las columnas de V24, unicidad por índice ciego y ausencia de
 * datos en claro en la BD. El trigger de inmutabilidad es exclusivo de PostgreSQL y se prueba con
 * scripts/probar-migracion-v24.sh. La clave se genera al azar en cada ejecución y las cédulas son sintéticas.
 */
@SpringBootTest(properties = {"app.api.role=usuarios", "app.messaging.rabbitmq.enabled=false",
        "spring.flyway.enabled=false"})
@ActiveProfiles("test")
@DisplayName("HU-13 Persistencia de datos de identidad en usuarios")
class UsuarioIdentidadPersistenciaTest {
    private static final LocalDate HOY = LocalDate.of(2026, 11, 7);

    @DynamicPropertySource
    static void claveAleatoria(DynamicPropertyRegistry registro) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String clave = Base64.getEncoder().encodeToString(bytes);
        registro.add("app.identidad.cifrado.clave-maestra", () -> clave);
    }

    @Autowired private UsuarioRepository usuarios;
    @Autowired private ProteccionDatosIdentidadService proteccion;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM usuarios WHERE email LIKE '%@hu13.invalid'");
    }

    private Usuario nuevoUsuario() {
        return Usuario.builder()
                .nombre("Usuario Sintético")
                .email(UUID.randomUUID() + "@hu13.invalid")
                .password("no-es-un-hash-real")
                .rol(Rol.CLIENTE)
                .estado(EstadoUsuario.ACTIVO)
                .aceptoTerminos(true)
                .fechaAceptacionTerminos(LocalDateTime.now())
                .build();
    }

    private Usuario conIdentidad(String cedula) {
        Usuario usuario = nuevoUsuario();
        usuario.registrarDatosIdentidad(proteccion.proteger(cedula, LocalDate.of(1990, 5, 17), HOY), Instant.now());
        return usuario;
    }

    @Test
    @DisplayName("Guarda solo cifrados y el índice ciego, nunca la cédula ni la fecha en claro (CA-8)")
    void persisteSoloCifrado() {
        Usuario guardado = usuarios.saveAndFlush(conIdentidad("1000000001"));

        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM usuarios WHERE id = ?", guardado.getId());
        assertThat(fila.values()).noneMatch(valor -> valor != null
                && (valor.toString().contains("1000000001") || valor.toString().contains("1990-05-17")));
        assertThat(fila.get("NUMERO_CEDULA_CIFRADO").toString()).startsWith("v1:");
        assertThat(fila.get("FECHA_NACIMIENTO_CIFRADA").toString()).startsWith("v1:");
        assertThat(fila.get("NUMERO_CEDULA_HASH").toString()).matches("^[0-9a-f]{64}$");
        assertThat(fila.get("DATOS_IDENTIDAD_BLOQUEADOS")).isEqualTo(true);
        assertThat(fila.get("FECHA_BLOQUEO_IDENTIDAD")).isNotNull();

        Usuario leido = usuarios.findById(guardado.getId()).orElseThrow();
        assertThat(proteccion.cedulaDe(leido).valor()).isEqualTo("1000000001");
        assertThat(proteccion.fechaNacimientoDe(leido)).isEqualTo(LocalDate.of(1990, 5, 17));
    }

    @Test
    @DisplayName("Negativo: la misma cédula en otro usuario, aunque cambie el formato, viola la unicidad")
    void cedulaDuplicadaRechazada() {
        usuarios.saveAndFlush(conIdentidad("1000000002"));
        assertThat(usuarios.existsByNumeroCedulaHash(proteccion.indiceCiego("1.000.000.002"))).isTrue();

        Usuario duplicado = conIdentidad("1.000.000.002");
        assertThatThrownBy(() -> usuarios.saveAndFlush(duplicado))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Varios usuarios previos a HU-13, sin identidad (NULL), conviven sin violar la unicidad")
    void usuariosSinIdentidad() {
        Usuario a = usuarios.saveAndFlush(nuevoUsuario());
        Usuario b = usuarios.saveAndFlush(nuevoUsuario());
        assertThat(usuarios.findById(a.getId()).orElseThrow().tieneDatosIdentidad()).isFalse();
        assertThat(usuarios.findById(b.getId()).orElseThrow().isDatosIdentidadBloqueados()).isFalse();
    }

    @Test
    @DisplayName("Negativo CA-3: después de recargar desde la BD, sigue sin poder registrarse otra identidad")
    void inmutableTrasRecargar() {
        Usuario guardado = usuarios.saveAndFlush(conIdentidad("1000000003"));
        Usuario recargado = usuarios.findById(guardado.getId()).orElseThrow();

        assertThatThrownBy(() -> recargado.registrarDatosIdentidad(
                proteccion.proteger("1000000004", LocalDate.of(2000, 1, 1), HOY), Instant.now()))
                .isInstanceOf(DatosIdentidadInmutablesException.class);
        assertThat(usuarios.existsByNumeroCedulaHash(proteccion.indiceCiego("1000000004"))).isFalse();
    }

    @Test
    @DisplayName("Negativo: la búsqueda por índice no es vulnerable a inyección (consulta parametrizada)")
    void busquedaParametrizada() {
        usuarios.saveAndFlush(conIdentidad("1000000005"));
        assertThat(usuarios.existsByNumeroCedulaHash("' OR '1'='1")).isFalse();
        assertThat(usuarios.existsByNumeroCedulaHash("%")).isFalse();
    }
}

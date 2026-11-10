package com.udea.demo.usuarios.domain.model;

import com.udea.demo.usuarios.domain.exception.DatosIdentidadInmutablesException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HU-13 Usuario: registro único e inmutable de los datos de identidad (CA-3)")
class UsuarioDatosIdentidadTest {
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    private static DatosIdentidadProtegidos datos(String hash) {
        return new DatosIdentidadProtegidos("v1:cifrado-sintetico-cedula", hash, "v1:cifrado-sintetico-fecha");
    }

    @Test
    @DisplayName("Un usuario nuevo o existente empieza sin datos de identidad y sin bloquear")
    void estadoInicial() {
        Usuario usuario = Usuario.builder().nombre("Sintético").email("u@ejemplo.invalid").build();
        assertThat(usuario.tieneDatosIdentidad()).isFalse();
        assertThat(usuario.isDatosIdentidadBloqueados()).isFalse();
        assertThat(usuario.getNumeroCedulaHash()).isNull();
        assertThat(usuario.getFechaBloqueoIdentidad()).isNull();
    }

    @Test
    @DisplayName("Registrar los datos los guarda, los bloquea y anota el momento en UTC")
    void registraYBloquea() {
        Usuario usuario = new Usuario();
        Instant ahora = Instant.parse("2026-11-07T03:15:00Z");

        usuario.registrarDatosIdentidad(datos(HASH_A), ahora);

        assertThat(usuario.tieneDatosIdentidad()).isTrue();
        assertThat(usuario.isDatosIdentidadBloqueados()).isTrue();
        assertThat(usuario.getNumeroCedulaHash()).isEqualTo(HASH_A);
        assertThat(usuario.getNumeroCedulaCifrado()).startsWith("v1:");
        assertThat(usuario.getFechaNacimientoCifrada()).startsWith("v1:");
        // Se guarda en UTC aunque la zona operativa sea America/Bogota (que el 7 de noviembre seguía en el día 6).
        assertThat(usuario.getFechaBloqueoIdentidad()).isEqualTo(LocalDateTime.of(2026, 11, 7, 3, 15));
    }

    @Test
    @DisplayName("Negativo: un segundo registro, incluso con los mismos datos, se rechaza y no cambia nada")
    void segundoRegistroRechazado() {
        Usuario usuario = new Usuario();
        usuario.registrarDatosIdentidad(datos(HASH_A), Instant.parse("2026-01-01T00:00:00Z"));

        assertThatThrownBy(() -> usuario.registrarDatosIdentidad(datos(HASH_B), Instant.now()))
                .isInstanceOf(DatosIdentidadInmutablesException.class);
        assertThatThrownBy(() -> usuario.registrarDatosIdentidad(datos(HASH_A), Instant.now()))
                .isInstanceOf(DatosIdentidadInmutablesException.class);

        assertThat(usuario.getNumeroCedulaHash()).isEqualTo(HASH_A);
        assertThat(usuario.getFechaBloqueoIdentidad()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
    }

    @Test
    @DisplayName("Negativo: no hay setters públicos para alterar ni desbloquear los datos de identidad")
    void sinSettersDeIdentidad() {
        assertThat(Arrays.stream(Usuario.class.getMethods()).map(Method::getName))
                .noneMatch(nombre -> nombre.startsWith("set") && (nombre.contains("Cedula")
                        || nombre.contains("FechaNacimiento") || nombre.contains("DatosIdentidad")
                        || nombre.contains("BloqueoIdentidad")));
    }

    @Test
    @DisplayName("Negativo: los datos protegidos son obligatorios")
    void datosObligatorios() {
        Usuario usuario = new Usuario();
        assertThatThrownBy(() -> usuario.registrarDatosIdentidad(null, Instant.now()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> usuario.registrarDatosIdentidad(datos(HASH_A), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DatosIdentidadProtegidos(null, HASH_A, "v1:x"))
                .isInstanceOf(NullPointerException.class);
        assertThat(usuario.tieneDatosIdentidad()).isFalse();
    }

    @Test
    @DisplayName("El toString de DatosIdentidadProtegidos no expone cifrados ni hashes (CA-8)")
    void toStringSinDatos() {
        assertThat(datos(HASH_A).toString()).doesNotContain(HASH_A).doesNotContain("v1:");
    }
}

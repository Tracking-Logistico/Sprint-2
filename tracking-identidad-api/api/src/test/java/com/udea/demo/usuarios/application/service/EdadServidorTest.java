package com.udea.demo.usuarios.application.service;

import com.udea.demo.usuarios.domain.exception.CifradoIdentidadException;
import com.udea.demo.usuarios.domain.model.Usuario;
import com.udea.demo.usuarios.infrastructure.cifrado.CifradorAesGcmDatosIdentidad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** HU-13 CA-2: la edad se calcula en el servidor, a la fecha actual, desde la fecha de nacimiento cifrada. */
@DisplayName("HU-13 Cálculo de edad en el servidor")
class EdadServidorTest {
    private final ProteccionDatosIdentidadService servicio = new ProteccionDatosIdentidadService(
            new CifradorAesGcmDatosIdentidad(Base64.getEncoder().encodeToString(new SecureRandom().generateSeed(32)), true));

    @ParameterizedTest
    @CsvSource({
            "2008-11-07, 2026-11-07, 18",   // cumple 18 hoy
            "2008-11-08, 2026-11-07, 17",   // le falta un día
            "2008-11-06, 2026-11-07, 18",
            "2004-02-29, 2022-02-28, 17",   // nacido un 29 de febrero, año no bisiesto: aún no cumple
            "2004-02-29, 2022-03-01, 18",
            "2004-02-29, 2024-02-29, 20",
            "2026-11-07, 2026-11-07, 0",
            "1900-01-01, 2026-11-07, 126"
    })
    @DisplayName("Años cumplidos exactos, incluidos los límites del cumpleaños y el 29 de febrero")
    void edadEn(LocalDate nacimiento, LocalDate hoy, int esperada) {
        assertThat(ProteccionDatosIdentidadService.edadEn(nacimiento, hoy)).isEqualTo(esperada);
    }

    @Test
    @DisplayName("Negativo: no calcula edades con fechas futuras o ausentes")
    void fechasInvalidas() {
        assertThatThrownBy(() -> ProteccionDatosIdentidadService.edadEn(LocalDate.of(2030, 1, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProteccionDatosIdentidadService.edadEn(null, LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Usuario conNacimiento(LocalDate nacimiento) {
        Usuario usuario = new Usuario();
        usuario.registrarDatosIdentidad(servicio.proteger("1000000001", nacimiento, servicio.hoy()), Instant.now());
        return usuario;
    }

    @Test
    @DisplayName("edadDe y esMayorDeEdad usan la fecha cifrada del usuario y la fecha actual de la zona operativa")
    void edadDelUsuario() {
        LocalDate hoy = servicio.hoy();
        assertThat(servicio.edadDe(conNacimiento(hoy.minusYears(30)))).isEqualTo(30);
        assertThat(servicio.esMayorDeEdad(conNacimiento(hoy.minusYears(18)))).isTrue();
        assertThat(servicio.esMayorDeEdad(conNacimiento(hoy.minusYears(18).plusDays(1)))).isFalse();
    }

    @Test
    @DisplayName("Negativo: sin fecha registrada no hay edad, no se asume ningún valor")
    void sinFechaNoHayEdad() {
        assertThatThrownBy(() -> servicio.edadDe(new Usuario())).isInstanceOf(CifradoIdentidadException.class);
    }
}

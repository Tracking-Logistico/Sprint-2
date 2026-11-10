package com.udea.demo.usuarios.application.service;

import com.udea.demo.usuarios.domain.exception.CifradoIdentidadException;
import com.udea.demo.usuarios.domain.exception.DatoIdentidadInvalidoException;
import com.udea.demo.usuarios.domain.model.DatosIdentidadProtegidos;
import com.udea.demo.usuarios.domain.model.Usuario;
import com.udea.demo.usuarios.infrastructure.cifrado.CifradorAesGcmDatosIdentidad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HU-13 ProteccionDatosIdentidadService: validación (CA-1) y protección (CA-8)")
class ProteccionDatosIdentidadServiceTest {
    private static final LocalDate HOY = LocalDate.of(2026, 11, 7);

    private final ProteccionDatosIdentidadService servicio =
            new ProteccionDatosIdentidadService(new CifradorAesGcmDatosIdentidad(claveAleatoria(), true));

    private static String claveAleatoria() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    @DisplayName("Protege y recupera la cédula normalizada y la fecha de nacimiento")
    void protegeYRecupera() {
        DatosIdentidadProtegidos datos = servicio.proteger("1.000.000.001", LocalDate.of(1990, 5, 17), HOY);
        Usuario usuario = new Usuario();
        usuario.registrarDatosIdentidad(datos, Instant.now());

        assertThat(servicio.cedulaDe(usuario).valor()).isEqualTo("1000000001");
        assertThat(servicio.fechaNacimientoDe(usuario)).isEqualTo(LocalDate.of(1990, 5, 17));
        assertThat(datos.numeroCedulaHash()).isEqualTo(servicio.indiceCiego("1000000001"));
        assertThat(datos.numeroCedulaCifrado()).doesNotContain("1000000001");
        assertThat(datos.fechaNacimientoCifrada()).doesNotContain("1990");
    }

    @Test
    @DisplayName("Dos formatos de la misma cédula producen el mismo índice, así que no se puede duplicar")
    void mismoIndiceConDistintoFormato() {
        assertThat(servicio.indiceCiego("1 000 000 001")).isEqualTo(servicio.indiceCiego("1000000001"));
    }

    @Test
    @DisplayName("Acepta como límites hoy mismo y el 1 de enero de 1900")
    void limitesDeFecha() {
        assertThat(servicio.proteger("1000000001", HOY, HOY)).isNotNull();
        assertThat(servicio.proteger("1000000001", LocalDate.of(1900, 1, 1), HOY)).isNotNull();
    }

    @Test
    @DisplayName("Negativo CA-1: rechaza una fecha de nacimiento futura con un mensaje del campo")
    void fechaFutura() {
        assertThatThrownBy(() -> servicio.proteger("1000000001", HOY.plusDays(1), HOY))
                .isInstanceOf(DatoIdentidadInvalidoException.class)
                .hasMessage("La fecha de nacimiento no puede ser futura")
                .extracting("campo").isEqualTo("fechaNacimiento");
    }

    @Test
    @DisplayName("Negativo CA-1: rechaza fechas no plausibles y fechas ausentes")
    void fechaNoPlausible() {
        assertThatThrownBy(() -> servicio.proteger("1000000001", LocalDate.of(1899, 12, 31), HOY))
                .isInstanceOf(DatoIdentidadInvalidoException.class)
                .hasMessage("La fecha de nacimiento no es una fecha válida");
        assertThatThrownBy(() -> servicio.proteger("1000000001", LocalDate.of(-5000, 1, 1), HOY))
                .isInstanceOf(DatoIdentidadInvalidoException.class);
        assertThatThrownBy(() -> servicio.proteger("1000000001", null, HOY))
                .isInstanceOf(DatoIdentidadInvalidoException.class)
                .hasMessage("La fecha de nacimiento es obligatoria");
    }

    @Test
    @DisplayName("Negativo CA-1: rechaza una cédula inválida antes de cifrar nada")
    void cedulaInvalida() {
        assertThatThrownBy(() -> servicio.proteger("1234567' OR '1'='1", LocalDate.of(1990, 1, 1), HOY))
                .isInstanceOf(DatoIdentidadInvalidoException.class)
                .extracting("campo").isEqualTo("numeroCedula");
        assertThatThrownBy(() -> servicio.indiceCiego("<img src=x onerror=alert(1)>"))
                .isInstanceOf(DatoIdentidadInvalidoException.class);
    }

    @Test
    @DisplayName("Negativo: no se puede leer la identidad de un usuario que no la tiene registrada")
    void usuarioSinIdentidad() {
        assertThatThrownBy(() -> servicio.cedulaDe(new Usuario())).isInstanceOf(CifradoIdentidadException.class);
        assertThatThrownBy(() -> servicio.fechaNacimientoDe(null)).isInstanceOf(CifradoIdentidadException.class);
    }

    @Test
    @DisplayName("Negativo: un cifrado de identidad ajeno (otra clave) no se puede leer")
    void cifradoAjeno() {
        ProteccionDatosIdentidadService otroEntorno =
                new ProteccionDatosIdentidadService(new CifradorAesGcmDatosIdentidad(claveAleatoria(), true));
        Usuario usuario = new Usuario();
        usuario.registrarDatosIdentidad(otroEntorno.proteger("1000000001", LocalDate.of(1990, 1, 1), HOY),
                Instant.now());
        assertThatThrownBy(() -> servicio.cedulaDe(usuario)).isInstanceOf(CifradoIdentidadException.class);
    }
}

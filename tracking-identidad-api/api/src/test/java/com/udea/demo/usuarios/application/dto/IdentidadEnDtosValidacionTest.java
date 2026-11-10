package com.udea.demo.usuarios.application.dto;

import com.udea.demo.usuarios.domain.model.Rol;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-13 CA-1: la forma de la cédula y la fecha se valida en el servidor, con un mensaje específico por campo. */
@DisplayName("HU-13 Validación de cédula y fecha de nacimiento en los DTOs de registro (HU-01A) y creación interna (HU-01B)")
class IdentidadEnDtosValidacionTest {
    private static Validator validator;

    @BeforeAll
    static void iniciar() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static RegistroClienteRequestDTO registro(String cedula, String fecha) {
        return new RegistroClienteRequestDTO("Ana Sintetica", "ana@ejemplo.invalid", "Password123!", "Password123!",
                "+573000000000", "Calle sintetica 1", "Medellin", true, "T-1", true, "P-1", cedula, fecha);
    }

    private static CrearUsuarioInternoCommand interno(String cedula, String fecha) {
        return new CrearUsuarioInternoCommand("Op Sintetico", "op@ejemplo.invalid", "3000000000", Rol.OPERADOR,
                "Calle 1", null, "OP-1", cedula, fecha);
    }

    private static <T> Set<String> camposConError(Set<ConstraintViolation<T>> violaciones) {
        return violaciones.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    private static <T> String mensajeDe(Set<ConstraintViolation<T>> violaciones, String campo) {
        return violaciones.stream().filter(v -> v.getPropertyPath().toString().equals(campo))
                .map(ConstraintViolation::getMessage).findFirst().orElse(null);
    }

    @Test
    @DisplayName("Con cédula y fecha bien formadas no hay violaciones")
    void validos() {
        assertThat(validator.validate(registro("1000000001", "1990-05-17"))).isEmpty();
        assertThat(validator.validate(interno("1000000001", "1990-05-17"))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Negativo: la cédula es obligatoria, con un mensaje propio")
    void cedulaObligatoria(String cedula) {
        var violaciones = validator.validate(registro(cedula, "1990-05-17"));
        assertThat(mensajeDe(violaciones, "numeroCedula")).isEqualTo("El número de cédula es obligatorio");
        assertThat(mensajeDe(validator.validate(interno(cedula, "1990-05-17")), "numeroCedula"))
                .isEqualTo("El número de cédula es obligatorio");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("Negativo: la fecha de nacimiento es obligatoria, con un mensaje propio")
    void fechaObligatoria(String fecha) {
        assertThat(mensajeDe(validator.validate(registro("1000000001", fecha)), "fechaNacimiento"))
                .isEqualTo("La fecha de nacimiento es obligatoria");
    }

    @ParameterizedTest
    @ValueSource(strings = {"17/05/1990", "1990-5-17", "19900517", "1990-05-17T10:00", "' OR 1=1--", "<b>1990</b>",
            "\u0661\u0669\u0669\u0660-\u0660\u0665-\u0661\u0667"})
    @DisplayName("Negativo: rechaza fechas con formato distinto de AAAA-MM-DD o con contenido malicioso")
    void fechaMalFormada(String fecha) {
        var violaciones = validator.validate(registro("1000000001", fecha));
        assertThat(camposConError(violaciones)).contains("fechaNacimiento");
        assertThat(mensajeDe(violaciones, "fechaNacimiento"))
                .isEqualTo("La fecha de nacimiento debe tener el formato AAAA-MM-DD")
                .doesNotContain(fecha);
    }

    @Test
    @DisplayName("Negativo: corta cédulas sobredimensionadas antes de procesarlas")
    void cedulaSobredimensionada() {
        var violaciones = validator.validate(registro("1".repeat(5_000), "1990-05-17"));
        assertThat(mensajeDe(violaciones, "numeroCedula")).isEqualTo("El número de cédula no tiene un formato válido");
    }

    @Test
    @DisplayName("CA-8: el toString de los DTOs no expone cédula, fecha ni contraseña")
    void toStringSinDatosSensibles() {
        assertThat(registro("1000000001", "1990-05-17").toString())
                .doesNotContain("1000000001").doesNotContain("1990-05-17").doesNotContain("Password123!")
                .doesNotContain("ana@ejemplo.invalid");
        assertThat(interno("1000000001", "1990-05-17").toString())
                .doesNotContain("1000000001").doesNotContain("1990-05-17").doesNotContain("op@ejemplo.invalid");
    }
}

package com.udea.demo.usuarios.application.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RegistroClienteRequestDTO - validación del registro de cliente")
class RegistroClienteRequestDTOTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private RegistroClienteRequestDTO valido() {
        return new RegistroClienteRequestDTO(
                "Ana María Gómez", "ana.gomez@tracking.com", "Password123!", "Password123!",
                "+573001234567", "Carrera 43A # 12-34, Medellín", "Medellín",
                true, "T&C-v1.0", true, "PDP-v1.0", "1000000001", "1990-05-17");
    }

    @Test
    @DisplayName("DTO canónico con términos y política de datos aceptados no produce violaciones")
    void dtoValido_sinViolaciones() {
        RegistroClienteRequestDTO dto = valido();

        Set<ConstraintViolation<RegistroClienteRequestDTO>> violations = validator.validate(dto);

        assertThat(violations).isEmpty();
        assertThat(dto.nombre()).isEqualTo("Ana María Gómez");
        assertThat(dto.email()).isEqualTo("ana.gomez@tracking.com");
        assertThat(dto.telefono()).isEqualTo("+573001234567");
        assertThat(dto.aceptoTerminos()).isTrue();
        assertThat(dto.aceptoPoliticaDatos()).isTrue();
        assertThat(dto.versionTerminos()).isEqualTo("T&C-v1.0");
        assertThat(dto.versionPoliticaDatos()).isEqualTo("PDP-v1.0");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Nombre en blanco produce violación")
    void nombreEnBlanco(String nombre) {
        RegistroClienteRequestDTO dto = new RegistroClienteRequestDTO(
                nombre, "ana.gomez@tracking.com", "Password123!", "Password123!",
                "+573001234567", "Carrera 43A # 12-34", "Medellín",
                true, "T&C-v1.0", true, "PDP-v1.0", "1000000001", "1990-05-17");

        assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("nombre"));
    }

    @Test
    @DisplayName("Email con formato inválido produce violación")
    void emailInvalido() {
        RegistroClienteRequestDTO dto = new RegistroClienteRequestDTO(
                "Ana María Gómez", "no-es-email", "Password123!", "Password123!",
                "+573001234567", "Carrera 43A # 12-34", "Medellín",
                true, "T&C-v1.0", true, "PDP-v1.0", "1000000001", "1990-05-17");

        assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ab1!", "password123!", "PASSWORD123!", "Password!!!!", "Passwordaaaa"})
    @DisplayName("Password corta o sin complejidad produce violación")
    void passwordInvalida(String password) {
        RegistroClienteRequestDTO dto = new RegistroClienteRequestDTO(
                "Ana María Gómez", "ana.gomez@tracking.com", password, password,
                "+573001234567", "Carrera 43A # 12-34", "Medellín",
                true, "T&C-v1.0", true, "PDP-v1.0", "1000000001", "1990-05-17");

        assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    @DisplayName("aceptoTerminos=false produce violación @AssertTrue")
    void noAceptaTerminos() {
        RegistroClienteRequestDTO dto = new RegistroClienteRequestDTO(
                "Ana María Gómez", "ana.gomez@tracking.com", "Password123!", "Password123!",
                "+573001234567", "Carrera 43A # 12-34", "Medellín",
                false, "T&C-v1.0", true, "PDP-v1.0", "1000000001", "1990-05-17");

        assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("aceptoTerminos"));
    }

    @Test
    @DisplayName("aceptoPoliticaDatos=false produce violación @AssertTrue")
    void noAceptaPoliticaDatos() {
        RegistroClienteRequestDTO dto = new RegistroClienteRequestDTO(
                "Ana María Gómez", "ana.gomez@tracking.com", "Password123!", "Password123!",
                "+573001234567", "Carrera 43A # 12-34", "Medellín",
                true, "T&C-v1.0", false, "PDP-v1.0", "1000000001", "1990-05-17");

        assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("aceptoPoliticaDatos"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234", "telefono-invalido"})
    @DisplayName("Teléfono con formato no internacional produce violación")
    void telefonoInvalido(String telefono) {
        RegistroClienteRequestDTO dto = new RegistroClienteRequestDTO(
                "Ana María Gómez", "ana.gomez@tracking.com", "Password123!", "Password123!",
                telefono, "Carrera 43A # 12-34", "Medellín",
                true, "T&C-v1.0", true, "PDP-v1.0", "1000000001", "1990-05-17");

        assertThat(validator.validate(dto)).anyMatch(v -> v.getPropertyPath().toString().equals("telefono"));
    }
}

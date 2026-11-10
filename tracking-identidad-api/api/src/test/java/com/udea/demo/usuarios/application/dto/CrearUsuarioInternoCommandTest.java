package com.udea.demo.usuarios.application.dto;

import com.udea.demo.usuarios.domain.model.Rol;
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

@DisplayName("CrearUsuarioInternoCommand - validación de alta de operador y conductor")
class CrearUsuarioInternoCommandTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private CrearUsuarioInternoCommand operadorValido() {
        return new CrearUsuarioInternoCommand(
                "Laura Ops", "laura.ops@tracking.com", "3001112233",
                Rol.OPERADOR, "Calle 10 # 40-20, Medellín", null, "OP-001", "1000000001", "1990-05-17");
    }

    @Test
    @DisplayName("Operador con Rol.OPERADOR y código de empleado no produce violaciones")
    void operadorValido_sinViolaciones() {
        CrearUsuarioInternoCommand cmd = operadorValido();

        Set<ConstraintViolation<CrearUsuarioInternoCommand>> violations = validator.validate(cmd);

        assertThat(violations).isEmpty();
        assertThat(cmd.rol()).isEqualTo(Rol.OPERADOR);
        assertThat(cmd.codigoEmpleado()).isEqualTo("OP-001");
        assertThat(cmd.licencia()).isNull();
    }

    @Test
    @DisplayName("Conductor con Rol.CONDUCTOR y licencia no produce violaciones")
    void conductorValido_sinViolaciones() {
        CrearUsuarioInternoCommand cmd = new CrearUsuarioInternoCommand(
                "Julián Castaño", "julian.castano@tracking.com", "3002223344",
                Rol.CONDUCTOR, "Calle 33 # 65-12, Medellín", "LIC-B2-998877", null, "1000000002", "1985-11-30");

        assertThat(validator.validate(cmd)).isEmpty();
        assertThat(cmd.rol()).isEqualTo(Rol.CONDUCTOR);
        assertThat(cmd.licencia()).isEqualTo("LIC-B2-998877");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Nombre en blanco produce violación")
    void nombreEnBlanco(String nombre) {
        CrearUsuarioInternoCommand cmd = new CrearUsuarioInternoCommand(
                nombre, "laura.ops@tracking.com", "3001112233",
                Rol.OPERADOR, "Calle 10 # 40-20", null, "OP-001");

        assertThat(validator.validate(cmd)).anyMatch(v -> v.getPropertyPath().toString().equals("nombre"));
    }

    @Test
    @DisplayName("Email inválido produce violación")
    void emailInvalido() {
        CrearUsuarioInternoCommand cmd = new CrearUsuarioInternoCommand(
                "Laura Ops", "sin-arroba", "3001112233",
                Rol.OPERADOR, "Calle 10 # 40-20", null, "OP-001");

        assertThat(validator.validate(cmd)).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    @DisplayName("Teléfono con letras produce violación @Pattern")
    void telefonoInvalido() {
        CrearUsuarioInternoCommand cmd = new CrearUsuarioInternoCommand(
                "Laura Ops", "laura.ops@tracking.com", "abc",
                Rol.OPERADOR, "Calle 10 # 40-20", null, "OP-001");

        assertThat(validator.validate(cmd)).anyMatch(v -> v.getPropertyPath().toString().equals("telefono"));
    }

    @Test
    @DisplayName("Rol nulo produce violación @NotNull")
    void rolNulo() {
        CrearUsuarioInternoCommand cmd = new CrearUsuarioInternoCommand(
                "Laura Ops", "laura.ops@tracking.com", "3001112233",
                null, "Calle 10 # 40-20", null, "OP-001");

        assertThat(validator.validate(cmd)).anyMatch(v -> v.getPropertyPath().toString().equals("rol"));
    }
}

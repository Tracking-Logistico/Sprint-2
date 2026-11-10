package com.udea.demo.config;

import com.udea.demo.usuarios.domain.exception.CifradoIdentidadException;
import com.udea.demo.usuarios.domain.exception.DatoIdentidadInvalidoException;
import com.udea.demo.usuarios.domain.exception.DatosIdentidadInmutablesException;
import com.udea.demo.usuarios.domain.model.NumeroCedula;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@DisplayName("HU-13 GlobalExceptionHandler: errores de identidad controlados y sin datos sensibles")
class GlobalExceptionHandlerIdentidadTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Un dato inválido devuelve 400 con un mensaje del campo, sin reflejar la entrada (CA-1)")
    void datoInvalido() {
        String malicioso = "<script>alert('1000000001')</script>";
        DatoIdentidadInvalidoException ex = catchThrowableOfType(DatoIdentidadInvalidoException.class,
                () -> NumeroCedula.de(malicioso));

        ResponseEntity<Map<String, String>> respuesta = handler.datoIdentidadInvalido(ex);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody()).containsKey("numeroCedula").containsEntry("error", "Revisa los campos señalados");
        assertThat(respuesta.getBody().toString()).doesNotContain("script").doesNotContain("1000000001");
    }

    @Test
    @DisplayName("Un intento de modificar la identidad bloqueada devuelve 409 IDENTIDAD_INMUTABLE (CA-3)")
    void inmutable() {
        ResponseEntity<Map<String, String>> respuesta =
                handler.identidadInmutable(new DatosIdentidadInmutablesException());
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody()).containsEntry("code", "IDENTIDAD_INMUTABLE");
    }

    @Test
    @DisplayName("Un fallo de cifrado devuelve 500 genérico, sin mensajes internos ni trazas (CA-8)")
    void falloCifrado() {
        ResponseEntity<Map<String, String>> respuesta = handler.cifradoIdentidad(
                new CifradoIdentidadException("El dato de identidad protegido no supera la verificación de integridad",
                        new RuntimeException("detalle interno")));
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(respuesta.getBody()).containsOnly(Map.entry("error", "No fue posible completar la operación"));
    }
}

package com.udea.demo.config;

import com.udea.demo.usuarios.application.dto.ActualizarPerfilRequestDTO;
import com.udea.demo.usuarios.application.dto.ActualizarRolUsuarioRequestDTO;
import com.udea.demo.usuarios.application.dto.CompletarIdentidadRequestDTO;
import com.udea.demo.usuarios.application.dto.RegistroClienteRequestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HU-13 CamposIdentidadProtegidosAdvice: detección de campos de identidad en el JSON")
class CamposIdentidadProtegidosAdviceTest {
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final CamposIdentidadProtegidosAdvice advice = new CamposIdentidadProtegidosAdvice(mapper);

    private boolean detecta(String json) {
        return CamposIdentidadProtegidosAdvice.buscarCampoIdentidad(mapper.readTree(json)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"numeroCedula\":\"1000000001\"}",
            "{\"numero_cedula\":\"1000000001\"}",
            "{\"NUMERO-CEDULA\":\"1000000001\"}",
            "{\"cedula\":1000000001}",
            "{\"fechaNacimiento\":\"1990-01-01\"}",
            "{\"fecha_nacimiento\":null}",
            "{\"numeroCedulaHash\":\"aaaa\"}",
            "{\"datosIdentidadBloqueados\":false}",
            "{\"perfil_incompleto\":false}",
            "{\"numeroDocumento\":\"x\"}",
            "{\"nombre\":\"Ana\",\"usuario\":{\"numeroCedula\":\"1\"}}",
            "{\"lista\":[{\"a\":1},{\"fechaNacimiento\":\"2000-01-01\"}]}",
            "[{\"cedula\":\"1\"}]"
    })
    @DisplayName("Negativo: detecta campos de identidad con cualquier grafía y en cualquier nivel")
    void detectaCamposIdentidad(String json) {
        assertThat(detecta(json)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"nombre\":\"Ana\",\"telefono\":\"+573000000000\",\"direccion\":\"Calle 1\"}",
            "{\"nombre\":\"Ana\",\"edad\":17}",
            "{\"rol\":\"CONDUCTOR\",\"licencia\":\"LIC-1\"}",
            "{\"nombre\":\"numeroCedula\"}",
            "{}", "[]", "\"texto\"", "42"
    })
    @DisplayName("No bloquea campos legítimos; la edad se ignora en vez de rechazarse (CA-2)")
    void permiteCamposLegitimos(String json) {
        assertThat(detecta(json)).isFalse();
    }

    @Test
    @DisplayName("Solo se aplica a los DTOs marcados con @SinCamposIdentidad, no al registro ni al completado")
    void soloDtosMarcados() {
        assertThat(advice.supports(null, ActualizarPerfilRequestDTO.class, null)).isTrue();
        assertThat(advice.supports(null, ActualizarRolUsuarioRequestDTO.class, null)).isTrue();
        assertThat(advice.supports(null, RegistroClienteRequestDTO.class, null)).isFalse();
        assertThat(advice.supports(null, CompletarIdentidadRequestDTO.class, null)).isFalse();
    }
}

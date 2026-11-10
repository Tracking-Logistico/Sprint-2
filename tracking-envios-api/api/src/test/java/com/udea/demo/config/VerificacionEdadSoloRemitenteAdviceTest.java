package com.udea.demo.config;

import com.udea.demo.pedidos.application.dto.CambiarEstadoLogisticoRequestDTO;
import com.udea.demo.pedidos.application.dto.RecibirPedidoRequestDTO;
import com.udea.demo.pedidos.application.dto.ValidarPedidoRequestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HU-13 VerificacionEdadSoloRemitenteAdvice: flag solo del remitente y whitelist de campos")
class VerificacionEdadSoloRemitenteAdviceTest {
    private final ObjectMapper mapper = JsonMapper.builder().build();

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"requiereVerificacionEdad\":true}", "{\"requiere_verificacion_edad\":false}",
            "{\"REQUIERE-VERIFICACION-EDAD\":1}", "{\"a\":{\"b\":{\"requiereVerificacionEdad\":true}}}",
            "{\"eventos\":[{\"x\":1},{\"requiereVerificacionEdad\":true}]}"
    })
    @DisplayName("Detecta el flag con cualquier grafía y a cualquier profundidad")
    void detectaFlag(String json) {
        assertThat(VerificacionEdadSoloRemitenteAdvice.contieneFlag(mapper.readTree(json))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"estado\":\"EN_TRANSITO\"}", "{\"motivo\":\"requiereVerificacionEdad\"}", "[]", "{}"})
    @DisplayName("No confunde valores ni campos legítimos con el flag")
    void noConfunde(String json) {
        assertThat(VerificacionEdadSoloRemitenteAdvice.contieneFlag(mapper.readTree(json))).isFalse();
    }

    @Test
    @DisplayName("Whitelist: solo las propiedades declaradas por el record, con nombre exacto")
    void whitelist() {
        assertThat(VerificacionEdadSoloRemitenteAdvice.soloCamposDeclarados(
                mapper.readTree("{\"estado\":\"EN_TRANSITO\"}"), CambiarEstadoLogisticoRequestDTO.class)).isTrue();
        assertThat(VerificacionEdadSoloRemitenteAdvice.soloCamposDeclarados(
                mapper.readTree("{\"estado\":\"EN_TRANSITO\",\"id\":1}"), CambiarEstadoLogisticoRequestDTO.class)).isFalse();
        assertThat(VerificacionEdadSoloRemitenteAdvice.soloCamposDeclarados(
                mapper.readTree("{\"aprobar\":true,\"observaciones\":\"ok\"}"), ValidarPedidoRequestDTO.class)).isTrue();
        assertThat(VerificacionEdadSoloRemitenteAdvice.soloCamposDeclarados(
                mapper.readTree("{\"requiereVerificacionEdad\":true,\"pesoKg\":1}"), RecibirPedidoRequestDTO.class)).isTrue();
        assertThat(VerificacionEdadSoloRemitenteAdvice.soloCamposDeclarados(
                mapper.readTree("{\"clienteId\":9}"), RecibirPedidoRequestDTO.class)).isFalse();
        assertThat(VerificacionEdadSoloRemitenteAdvice.soloCamposDeclarados(
                mapper.readTree("{\"PesoKg\":1}"), RecibirPedidoRequestDTO.class)).as("nombre exacto").isFalse();
    }
}

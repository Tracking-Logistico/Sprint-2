package com.udea.demo.pedidos.application.service;

import com.udea.demo.pedidos.domain.model.EstadoPedido;
import com.udea.demo.usuarios.application.service.PlantillaMensaje;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-13 13-FE-04 (CA-7), parte backend: los textos que ve el cliente (seguimiento y notificaciones) usan lenguaje de
 * cliente y nunca exponen códigos internos de estado. Se recorren TODOS los estados, incluidos los que se agreguen
 * en el futuro.
 */
@DisplayName("HU-13 Mensajes al cliente sin códigos internos")
class MensajesClienteSinCodigosTest {
    private final PlantillaMensaje plantilla = new PlantillaMensaje("https://seguimiento.ejemplo.invalid/tracking");

    @ParameterizedTest
    @EnumSource(EstadoPedido.class)
    @DisplayName("Negativo: ningún mensaje de seguimiento ni notificación contiene el código del estado")
    void sinCodigosInternos(EstadoPedido estado) {
        String seguimiento = MensajesSeguimientoCliente.estado(estado);
        String notificacion = plantilla.porEstado("LTSINTETICO1", estado).cuerpo();
        for (String texto : new String[]{seguimiento, notificacion}) {
            // Código en mayúsculas o con guion bajo (ENTREGA_FALLIDA_...), o la versión con espacios de un código
            // compuesto ("entrega fallida verificacion edad"). Las palabras sueltas como "entregado" sí son
            // lenguaje de cliente.
            assertThat(texto).doesNotContain(estado.name()).doesNotContain("_");
            if (estado.name().contains("_")) {
                assertThat(texto).doesNotContainIgnoringCase(estado.name().replace('_', ' '));
            }
        }
    }

    @Test
    @DisplayName("CA-7: la entrega fallida por edad se explica al cliente y anuncia el retorno a bodega")
    void entregaFallidaPorEdad() {
        String seguimiento = MensajesSeguimientoCliente.estado(EstadoPedido.ENTREGA_FALLIDA_VERIFICACION_EDAD);
        String notificacion = plantilla.porEstado("LTSINTETICO1", EstadoPedido.ENTREGA_FALLIDA_VERIFICACION_EDAD).cuerpo();
        for (String texto : new String[]{seguimiento, notificacion}) {
            assertThat(texto).contains("No pudimos entregar tu paquete").contains("mayoría de edad").contains("bodega")
                    // Sin datos personales del destinatario: ni cédula, ni fecha de nacimiento, ni edad.
                    .doesNotContainPattern("[0-9]{6,}").doesNotContainPattern("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                    .doesNotContainIgnoringCase("cédula").doesNotContainIgnoringCase("nacimiento");
        }
        assertThat(seguimiento).isNotEqualTo("Tu envío está en proceso");
    }
}

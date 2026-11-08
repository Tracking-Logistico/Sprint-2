package com.udea.demo.pedidos.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HU-13 Pedido: flag de verificación de edad 18+ (CA-4)")
class PedidoVerificacionEdadTest {

    private static Pedido enEstado(EstadoPedido estado) {
        return Pedido.builder().numeroPedido("SINTETICO-1").clienteId(1L).estado(estado).build();
    }

    @Test
    @DisplayName("Un envío nuevo o anterior a HU-13 no requiere verificación de edad")
    void porDefectoFalso() {
        assertThat(enEstado(EstadoPedido.SOLICITADO).isRequiereVerificacionEdad()).isFalse();
        assertThat(new Pedido().isRequiereVerificacionEdad()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, names = {"SOLICITADO", "CORRECCION_SOLICITADA"})
    @DisplayName("Se activa y desactiva mientras el remitente puede editar el envío")
    void editableAntesDeValidar(EstadoPedido estado) {
        Pedido pedido = enEstado(estado);
        pedido.definirVerificacionEdad(true);
        assertThat(pedido.isRequiereVerificacionEdad()).isTrue();
        pedido.definirVerificacionEdad(false);
        assertThat(pedido.isRequiereVerificacionEdad()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, mode = EnumSource.Mode.EXCLUDE,
            names = {"SOLICITADO", "CORRECCION_SOLICITADA"})
    @DisplayName("Negativo: después de la validación nadie puede cambiarlo, en ningún estado")
    void inmutableTrasValidar(EstadoPedido estado) {
        Pedido pedido = enEstado(estado);
        assertThatThrownBy(() -> pedido.definirVerificacionEdad(true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("La verificación de edad ya no se puede modificar en este estado del envío");
        assertThat(pedido.isRequiereVerificacionEdad()).isFalse();
    }

    @Test
    @DisplayName("Negativo: al aprobar, el envío sigue en SOLICITADO pero ya no admite cambios del flag")
    void inmutableTrasAprobarAunqueSigaSolicitado() {
        Pedido pedido = Pedido.builder().numeroPedido("SINTETICO-2").clienteId(1L)
                .prioridadSugerida(Prioridad.MEDIA).estado(EstadoPedido.SOLICITADO).build();
        pedido.definirVerificacionEdad(true);

        pedido.validar(true, null, "ok", 99L);

        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.SOLICITADO);
        assertThatThrownBy(() -> pedido.definirVerificacionEdad(false)).isInstanceOf(IllegalStateException.class);
        assertThat(pedido.isRequiereVerificacionEdad()).isTrue();
    }

    @Test
    @DisplayName("En el ciclo de corrección el remitente puede volver a cambiar el flag")
    void editableDuranteCorreccion() {
        Pedido pedido = Pedido.builder().numeroPedido("SINTETICO-3").clienteId(1L)
                .prioridadSugerida(Prioridad.MEDIA).estado(EstadoPedido.SOLICITADO).build();
        pedido.solicitarCorreccion("Ajustar dirección", 99L);

        pedido.definirVerificacionEdad(true);

        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.CORRECCION_SOLICITADA);
        assertThat(pedido.isRequiereVerificacionEdad()).isTrue();
    }

    @Test
    @DisplayName("Negativo: no hay un setter público que permita saltarse la regla de estado")
    void sinSetter() {
        assertThat(Arrays.stream(Pedido.class.getMethods()).map(Method::getName))
                .noneMatch(nombre -> nombre.startsWith("set") && nombre.contains("VerificacionEdad"));
    }
}

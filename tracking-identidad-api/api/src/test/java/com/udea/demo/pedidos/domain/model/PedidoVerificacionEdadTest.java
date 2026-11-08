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

    private static Pedido envio18EnEstado(EstadoPedido estado) {
        Pedido pedido = enEstado(EstadoPedido.SOLICITADO);
        pedido.definirVerificacionEdad(true);
        org.springframework.test.util.ReflectionTestUtils.setField(pedido, "estado", estado);
        return pedido;
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, names = {"EN_REPARTO", "ENTREGA_REPROGRAMADA"})
    @DisplayName("CA-7: durante la entrega, 'Verificación no aprobada' lleva al estado final de entrega fallida por edad")
    void fallaPorVerificacionEdad(EstadoPedido estado) {
        Pedido pedido = envio18EnEstado(estado);
        java.time.LocalDateTime ahora = java.time.LocalDateTime.of(2026, 11, 7, 10, 0);

        pedido.fallarPorVerificacionEdad(ahora);

        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.ENTREGA_FALLIDA_VERIFICACION_EDAD);
        assertThat(pedido.getEstado().esFinal()).isTrue();
        assertThat(pedido.getFechaEntregaFallida()).isEqualTo(ahora);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, names = {"CREADO", "EN_TRANSITO", "ENTREGADO", "CANCELADO", "PAUSADO",
            "ENTREGA_FALLIDA_VERIFICACION_EDAD"})
    @DisplayName("Negativo CA-7: fuera de la entrega, o ya finalizado, no se puede fallar por edad")
    void noFallaFueraDeEntrega(EstadoPedido estado) {
        Pedido pedido = envio18EnEstado(estado);
        assertThatThrownBy(() -> pedido.fallarPorVerificacionEdad(java.time.LocalDateTime.now()))
                .isInstanceOf(RuntimeException.class);
        assertThat(pedido.getEstado()).isEqualTo(estado);
    }

    @Test
    @DisplayName("Negativo CA-7: un envío sin la marca 18+ no puede fallar por verificación de edad")
    void sinMarcaNoFalla() {
        Pedido pedido = enEstado(EstadoPedido.EN_REPARTO);
        assertThatThrownBy(() -> pedido.fallarPorVerificacionEdad(java.time.LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Negativo CA-5 (13-BE-04): ningún cambio de estado genérico cierra como ENTREGADO un envío 18+")
    void entregadoGenericoBloqueado() {
        Pedido pedido = envio18EnEstado(EstadoPedido.EN_REPARTO);
        assertThat(pedido.puedeCambiarEstadoLogisticoA(EstadoPedido.ENTREGADO)).isFalse();
        assertThatThrownBy(() -> pedido.cambiarEstadoLogistico(EstadoPedido.ENTREGADO))
                .isInstanceOf(com.udea.demo.pedidos.domain.exception.VerificacionEdadRequeridaException.class)
                .hasMessage("El envío requiere verificación de edad aprobada antes de marcarse como entregado");
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.EN_REPARTO);
    }

    @Test
    @DisplayName("Negativo CA-5: sin 'Mayor de edad verificado' el cierre explícito también se rechaza")
    void entregaSinVerificacion() {
        Pedido pedido = envio18EnEstado(EstadoPedido.EN_REPARTO);
        assertThatThrownBy(() -> pedido.entregarConVerificacionEdad(false))
                .isInstanceOf(com.udea.demo.pedidos.domain.exception.VerificacionEdadRequeridaException.class);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.EN_REPARTO);
    }

    @Test
    @DisplayName("CA-6: con 'Mayor de edad verificado' el envío 18+ se cierra como ENTREGADO")
    void entregaConVerificacion() {
        Pedido pedido = envio18EnEstado(EstadoPedido.EN_REPARTO);
        pedido.entregarConVerificacionEdad(true);
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.ENTREGADO);
    }

    @Test
    @DisplayName("Sin regresión: un envío sin marca 18+ se entrega por cualquiera de los dos caminos")
    void envioNormalSinCambios() {
        Pedido a = enEstado(EstadoPedido.EN_REPARTO);
        a.cambiarEstadoLogistico(EstadoPedido.ENTREGADO);
        assertThat(a.getEstado()).isEqualTo(EstadoPedido.ENTREGADO);
        Pedido b = enEstado(EstadoPedido.EN_REPARTO);
        b.entregarConVerificacionEdad(false);
        assertThat(b.getEstado()).isEqualTo(EstadoPedido.ENTREGADO);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, names = {"CREADO", "EN_TRANSITO", "ENTREGA_REPROGRAMADA"})
    @DisplayName("Negativo: aun con la edad verificada, solo se entrega desde EN_REPARTO")
    void entregaFueraDeReparto(EstadoPedido estado) {
        Pedido pedido = envio18EnEstado(estado);
        assertThatThrownBy(() -> pedido.entregarConVerificacionEdad(true))
                .isInstanceOf(com.udea.demo.pedidos.domain.exception.TransicionEstadoInvalidaException.class);
        assertThat(pedido.getEstado()).isEqualTo(estado);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, names = {"ENTREGADO", "ENTREGA_FALLIDA_VERIFICACION_EDAD", "CANCELADO"})
    @DisplayName("Negativo: un envío finalizado no se vuelve a entregar")
    void finalizadoNoSeEntrega(EstadoPedido estado) {
        Pedido pedido = envio18EnEstado(estado);
        assertThatThrownBy(() -> pedido.entregarConVerificacionEdad(true)).isInstanceOf(RuntimeException.class);
        assertThat(pedido.getEstado()).isEqualTo(estado);
    }

    @Test
    @DisplayName("Negativo: no hay un setter público que permita saltarse la regla de estado")
    void sinSetter() {
        assertThat(Arrays.stream(Pedido.class.getMethods()).map(Method::getName))
                .noneMatch(nombre -> nombre.startsWith("set") && nombre.contains("VerificacionEdad"));
    }
}

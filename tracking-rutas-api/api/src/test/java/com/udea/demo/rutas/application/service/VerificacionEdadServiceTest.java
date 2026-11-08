package com.udea.demo.rutas.application.service;

import com.udea.demo.pedidos.application.service.RegistroHistorialPedido;
import com.udea.demo.pedidos.domain.event.DevolucionIniciadaEvent;
import com.udea.demo.pedidos.domain.model.*;
import com.udea.demo.pedidos.interfaces.persistence.PedidoRepository;
import com.udea.demo.rutas.application.dto.VerificacionEdadRequestDTO;
import com.udea.demo.rutas.application.dto.VerificacionEdadResponseDTO;
import com.udea.demo.rutas.domain.model.*;
import com.udea.demo.rutas.interfaces.persistence.ParadaRutaRepository;
import com.udea.demo.rutas.interfaces.persistence.RutaRepository;
import com.udea.demo.rutas.interfaces.persistence.VerificacionEdadLogRepository;
import com.udea.demo.usuarios.application.service.ActorAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HU-13 VerificacionEdadService: resultado del conductor (CA-5/6/7)")
class VerificacionEdadServiceTest {
    private static final Long PEDIDO = 70L;
    private static final Long USUARIO_CONDUCTOR = 7L;
    private static final Long CONDUCTOR = 17L;

    @Mock private PedidoRepository pedidos;
    @Mock private ParadaRutaRepository paradas;
    @Mock private RutaRepository rutas;
    @Mock private VerificacionEdadLogRepository verificaciones;
    @Mock private RegistroHistorialPedido historial;
    @Mock private ActorAuthorizationService actores;
    @Mock private ApplicationEventPublisher publisher;

    private VerificacionEdadService servicio;
    private Pedido pedido;
    private Ruta ruta;

    @BeforeEach
    void preparar() {
        servicio = new VerificacionEdadService(pedidos, paradas, rutas, verificaciones, historial, actores, publisher);
        lenient().when(actores.conductorActualUsuarioId()).thenReturn(USUARIO_CONDUCTOR);
        lenient().when(actores.conductorActualId()).thenReturn(CONDUCTOR);
        pedido = envio(EstadoPedido.EN_REPARTO, true);
        ruta = Ruta.crear(CONDUCTOR, LocalDate.of(2026, 11, 7));
        ruta.agregarParada(PEDIDO);
        lenient().when(pedidos.findByIdForUpdate(PEDIDO)).thenReturn(Optional.of(pedido));
        lenient().when(paradas.findByPedidoIdAndEstado(PEDIDO, EstadoParada.PENDIENTE))
                .thenReturn(Optional.of(ruta.getParadas().get(0)));
        lenient().when(verificaciones.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static Pedido envio(EstadoPedido estado, boolean requiere18) {
        Pedido p = Pedido.builder().id(PEDIDO).numeroPedido("SINT-1").clienteId(1L).estado(estado)
                .prioridadSugerida(Prioridad.MEDIA).build();
        ReflectionTestUtils.setField(p, "requiereVerificacionEdad", requiere18);
        return p;
    }

    private static VerificacionEdadRequestDTO solicitud(ResultadoVerificacionEdad r) {
        return new VerificacionEdadRequestDTO(r, null);
    }

    @Test
    @DisplayName("CA-6 CUMPLE: registra solo el resultado, deja el envío en reparto y habilita la entrega")
    void cumple() {
        VerificacionEdadResponseDTO res = servicio.registrar(PEDIDO, solicitud(ResultadoVerificacionEdad.CUMPLE));

        ArgumentCaptor<VerificacionEdadLog> log = ArgumentCaptor.forClass(VerificacionEdadLog.class);
        verify(verificaciones).saveAndFlush(log.capture());
        assertThat(log.getValue().getPedidoId()).isEqualTo(PEDIDO);
        assertThat(log.getValue().getConductorUsuarioId()).isEqualTo(USUARIO_CONDUCTOR);
        assertThat(log.getValue().getResultado()).isEqualTo(ResultadoVerificacionEdad.CUMPLE);
        assertThat(res.puedeEntregarse()).isTrue();
        assertThat(res.duplicado()).isFalse();
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.EN_REPARTO);
        verify(historial).registrar(eq(PEDIDO), eq(USUARIO_CONDUCTOR), eq("VERIFICACION_EDAD"), eq("EN_LINEA"),
                eq("Mayor de edad verificado"), any());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("CA-7 NO_CUMPLE: estado de entrega fallida por edad, parada cancelada y retorno a bodega, con el log antes del cambio de estado")
    void noCumple() {
        VerificacionEdadResponseDTO res = servicio.registrar(PEDIDO,
                new VerificacionEdadRequestDTO(ResultadoVerificacionEdad.NO_CUMPLE, OrigenCheckpoint.OFFLINE));

        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.ENTREGA_FALLIDA_VERIFICACION_EDAD);
        assertThat(ruta.getParadas().get(0).getEstado()).isEqualTo(EstadoParada.CANCELADA);
        assertThat(res.puedeEntregarse()).isFalse();
        assertThat(res.estadoPedido()).isEqualTo("ENTREGA_FALLIDA_VERIFICACION_EDAD");
        ArgumentCaptor<DevolucionIniciadaEvent> evento = ArgumentCaptor.forClass(DevolucionIniciadaEvent.class);
        verify(publisher).publishEvent(evento.capture());
        assertThat(evento.getValue().pedidoId()).isEqualTo(PEDIDO);
        assertThat(evento.getValue().motivo()).isEqualTo(MotivoDevolucion.VERIFICACION_EDAD_NO_APROBADA);
        assertThat(evento.getValue().estadoAnterior()).isEqualTo(EstadoPedido.EN_REPARTO);
        verify(historial).registrar(eq(PEDIDO), eq(USUARIO_CONDUCTOR), eq("VERIFICACION_EDAD"), eq("OFFLINE"),
                eq("Verificación no aprobada"), any());
        // Los triggers de V25/V27 exigen el log en la BD antes del cambio de estado.
        InOrder orden = inOrder(verificaciones, pedidos);
        orden.verify(verificaciones).saveAndFlush(any());
        orden.verify(pedidos).save(pedido);
    }

    @Test
    @DisplayName("Negativo anti-IDOR: un conductor ajeno recibe 403 y no se registra nada")
    void conductorAjeno() {
        when(actores.conductorActualId()).thenReturn(999L);
        assertThatThrownBy(() -> servicio.registrar(PEDIDO, solicitud(ResultadoVerificacionEdad.CUMPLE)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage(VerificacionEdadService.NO_ASIGNADO);
        verify(verificaciones, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Negativo anti-enumeración: un envío inexistente responde exactamente igual que uno ajeno")
    void envioInexistente() {
        when(pedidos.findByIdForUpdate(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.registrar(404L, solicitud(ResultadoVerificacionEdad.CUMPLE)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage(VerificacionEdadService.NO_ASIGNADO);
    }

    @Test
    @DisplayName("Negativo: un envío reasignado (sin parada pendiente del conductor) se rechaza")
    void sinParadaPendiente() {
        when(paradas.findByPedidoIdAndEstado(PEDIDO, EstadoParada.PENDIENTE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.registrar(PEDIDO, solicitud(ResultadoVerificacionEdad.NO_CUMPLE)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Negativo: un envío sin la marca 18+ no admite verificación")
    void sinMarca18() {
        when(pedidos.findByIdForUpdate(PEDIDO)).thenReturn(Optional.of(envio(EstadoPedido.EN_REPARTO, false)));
        assertThatThrownBy(() -> servicio.registrar(PEDIDO, solicitud(ResultadoVerificacionEdad.CUMPLE)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("El envío no requiere verificación de edad");
        verify(verificaciones, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoPedido.class, names = {"CREADO", "EN_TRANSITO", "ENTREGADO", "CANCELADO", "PAUSADO"})
    @DisplayName("Negativo: fuera de la entrega no se registra la verificación")
    void fueraDeEntrega(EstadoPedido estado) {
        when(pedidos.findByIdForUpdate(PEDIDO)).thenReturn(Optional.of(envio(estado, true)));
        assertThatThrownBy(() -> servicio.registrar(PEDIDO, solicitud(ResultadoVerificacionEdad.CUMPLE)))
                .isInstanceOf(IllegalStateException.class);
        verify(verificaciones, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Idempotencia: un reintento idéntico responde duplicado sin volver a escribir")
    void reintentoIdentico() {
        when(verificaciones.findById(PEDIDO)).thenReturn(Optional.of(
                VerificacionEdadLog.registrar(PEDIDO, USUARIO_CONDUCTOR, ResultadoVerificacionEdad.CUMPLE, Instant.now())));
        VerificacionEdadResponseDTO res = servicio.registrar(PEDIDO, solicitud(ResultadoVerificacionEdad.CUMPLE));
        assertThat(res.duplicado()).isTrue();
        verify(verificaciones, never()).saveAndFlush(any());
        verifyNoInteractions(historial, publisher);
    }

    @Test
    @DisplayName("Negativo: no se puede cambiar un resultado ya registrado (por ejemplo, de NO_CUMPLE a CUMPLE)")
    void cambioDeResultado() {
        when(verificaciones.findById(PEDIDO)).thenReturn(Optional.of(
                VerificacionEdadLog.registrar(PEDIDO, USUARIO_CONDUCTOR, ResultadoVerificacionEdad.NO_CUMPLE, Instant.now())));
        assertThatThrownBy(() -> servicio.registrar(PEDIDO, solicitud(ResultadoVerificacionEdad.CUMPLE)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("La verificación de edad de este envío ya fue registrada");
    }

    @Test
    @DisplayName("Negativo: el resultado registrado por otro conductor no se revela ni se reutiliza")
    void resultadoDeOtroConductor() {
        when(verificaciones.findById(PEDIDO)).thenReturn(Optional.of(
                VerificacionEdadLog.registrar(PEDIDO, 555L, ResultadoVerificacionEdad.CUMPLE, Instant.now())));
        assertThatThrownBy(() -> servicio.registrar(PEDIDO, solicitud(ResultadoVerificacionEdad.CUMPLE)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage(VerificacionEdadService.NO_ASIGNADO);
    }

    @Test
    @DisplayName("Negativo: sin resultado la solicitud se rechaza")
    void sinResultado() {
        assertThatThrownBy(() -> servicio.registrar(PEDIDO, new VerificacionEdadRequestDTO(null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.registrar(PEDIDO, null)).isInstanceOf(IllegalArgumentException.class);
    }
}

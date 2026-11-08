package com.udea.demo.rutas.application.service;

import com.udea.demo.pedidos.application.service.RegistroHistorialPedido;
import com.udea.demo.pedidos.domain.model.*;
import com.udea.demo.pedidos.domain.event.*;
import com.udea.demo.pedidos.domain.service.ValidadorUbicacion;
import com.udea.demo.pedidos.interfaces.persistence.IncidenciaPedidoRepository;
import com.udea.demo.pedidos.interfaces.persistence.PedidoRepository;
import com.udea.demo.rutas.application.dto.ResultadoEntregaRequestDTO;
import com.udea.demo.rutas.application.dto.ResultadoEntregaResponseDTO;
import com.udea.demo.rutas.domain.model.EstadoParada;
import com.udea.demo.rutas.domain.model.EventoResultadoEntrega;
import com.udea.demo.rutas.domain.model.ParadaRuta;
import com.udea.demo.rutas.domain.model.Ruta;
import com.udea.demo.rutas.interfaces.persistence.EventoResultadoEntregaRepository;
import com.udea.demo.rutas.interfaces.persistence.ParadaRutaRepository;
import com.udea.demo.rutas.interfaces.persistence.RutaRepository;
import com.udea.demo.usuarios.application.service.ActorAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RegistroResultadoEntregaService - registro offline/online de entregas")
class RegistroResultadoEntregaServiceTest {

    @Mock private EventoResultadoEntregaRepository eventosRegistrados;
    @Mock private ParadaRutaRepository paradas;
    @Mock private RutaRepository rutas;
    @Mock private PedidoRepository pedidos;
    @Mock private IncidenciaPedidoRepository incidencias;
    @Mock private RegistroHistorialPedido historial;
    @Mock private ValidadorUbicacion ubicaciones;
    @Mock private ActorAuthorizationService actores;
    @Mock private ApplicationEventPublisher publisher;
    @Mock private com.udea.demo.rutas.interfaces.persistence.VerificacionEdadLogRepository verificacionesEdad;

    private RegistroResultadoEntregaService service;

    private static final Long USUARIO_ID = 5L;
    private static final Long CONDUCTOR_ID = 10L;

    @BeforeEach
    void setUp() {
        service = new RegistroResultadoEntregaService(
                eventosRegistrados, paradas, rutas, pedidos, incidencias,
                historial, ubicaciones, actores, publisher, 3, verificacionesEdad);
        lenient().when(actores.conductorActualUsuarioId()).thenReturn(USUARIO_ID);
        lenient().when(actores.conductorActualId()).thenReturn(CONDUCTOR_ID);
    }

    @Test
    @DisplayName("Lanza IllegalArgumentException si el evento es nulo o incompleto")
    void registrar_eventoIncompleto_lanzaExcepcion() {
        assertThatThrownBy(() -> service.registrar(1L, null))
                .isInstanceOf(IllegalArgumentException.class);

        ResultadoEntregaRequestDTO reqSinFecha = new ResultadoEntregaRequestDTO(
                ResultadoEntregaRequestDTO.Resultado.ENTREGADO, null, null, null, null, null, UUID.randomUUID().toString());

        assertThatThrownBy(() -> service.registrar(1L, reqSinFecha))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Retorna respuesta con DUPLICADO si el evento ya fue registrado idéntico")
    void registrar_eventoDuplicadoIdentico_retornaDuplicado() {
        UUID eventId = UUID.randomUUID();
        OffsetDateTime fecha = OffsetDateTime.now().minusMinutes(2);
        ResultadoEntregaRequestDTO req = new ResultadoEntregaRequestDTO(
                ResultadoEntregaRequestDTO.Resultado.ENTREGADO,
                null, null, 6.24, -75.58, fecha, eventId.toString());

        EventoResultadoEntrega previo = new EventoResultadoEntrega(eventId.toString(), 1L, USUARIO_ID, req);
        when(eventosRegistrados.findById(eventId.toString())).thenReturn(Optional.of(previo));

        ResultadoEntregaResponseDTO res = service.registrar(1L, req);

        assertThat(res.estado()).isEqualTo("DUPLICADO");
        assertThat(res.duplicado()).isTrue();
    }

    @Test
    @DisplayName("Lanza IllegalStateException si el ID ya fue usado con datos diferentes")
    void registrar_eventoDuplicadoDiferente_lanzaExcepcion() {
        UUID eventId = UUID.randomUUID();
        OffsetDateTime fecha = OffsetDateTime.now().minusMinutes(2);
        ResultadoEntregaRequestDTO req = new ResultadoEntregaRequestDTO(
                ResultadoEntregaRequestDTO.Resultado.ENTREGADO,
                null, null, 6.24, -75.58, fecha, eventId.toString());

        ResultadoEntregaRequestDTO reqDistinto = new ResultadoEntregaRequestDTO(
                ResultadoEntregaRequestDTO.Resultado.ENTREGA_FALLIDA,
                "CLIENTE_AUSENTE", "No estaba", 6.24, -75.58, fecha, eventId.toString());
        EventoResultadoEntrega previo = new EventoResultadoEntrega(eventId.toString(), 1L, USUARIO_ID, reqDistinto);
        when(eventosRegistrados.findById(eventId.toString())).thenReturn(Optional.of(previo));

        assertThatThrownBy(() -> service.registrar(1L, req))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Lanza IllegalArgumentException si la fecha es mayor a la tolerancia o menor a 24 horas")
    void registrar_fechaFueraDeTolerancia_lanzaExcepcion() {
        UUID eventId = UUID.randomUUID();
        Pedido pedido = Pedido.builder().id(1L).estado(EstadoPedido.EN_REPARTO).build();
        when(eventosRegistrados.findById(any())).thenReturn(Optional.empty());
        when(pedidos.findByIdForUpdate(1L)).thenReturn(Optional.of(pedido));

        // Futuro > 5 min
        ResultadoEntregaRequestDTO reqFuturo = new ResultadoEntregaRequestDTO(
                ResultadoEntregaRequestDTO.Resultado.ENTREGADO,
                null, null, null, null, OffsetDateTime.now().plusHours(1), eventId.toString());

        assertThatThrownBy(() -> service.registrar(1L, reqFuturo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("posterior a la tolerancia");

        // Pasado > 24 horas
        ResultadoEntregaRequestDTO reqViejo = new ResultadoEntregaRequestDTO(
                ResultadoEntregaRequestDTO.Resultado.ENTREGADO,
                null, null, null, null, OffsetDateTime.now().minusDays(2), eventId.toString());

        assertThatThrownBy(() -> service.registrar(1L, reqViejo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("supera la ventana");
    }

    @Test
    @DisplayName("Registra entrega exitosa ENTREGADO cambiando estado y publicando evento")
    void registrar_entregaExitosa_aplica() {
        UUID eventId = UUID.randomUUID();
        OffsetDateTime fecha = OffsetDateTime.now().minusMinutes(5);
        ResultadoEntregaRequestDTO req = new ResultadoEntregaRequestDTO(
                ResultadoEntregaRequestDTO.Resultado.ENTREGADO,
                null, null, 6.25, -75.59, fecha, eventId.toString());

        Pedido pedido = Pedido.builder().id(10L).estado(EstadoPedido.EN_REPARTO).build();
        Ruta ruta = Ruta.builder().id(100L).conductorId(CONDUCTOR_ID).build();
        ParadaRuta parada = ParadaRuta.builder().id(50L).ruta(ruta).pedidoId(10L).estado(EstadoParada.PENDIENTE).build();
        ruta.getParadas().add(parada);

        when(eventosRegistrados.findById(any())).thenReturn(Optional.empty());
        when(pedidos.findByIdForUpdate(10L)).thenReturn(Optional.of(pedido));
        when(paradas.findByPedidoIdAndEstado(10L, EstadoParada.PENDIENTE)).thenReturn(Optional.of(parada));
        when(ubicaciones.evaluar(anyDouble(), anyDouble(), any())).thenReturn(new UbicacionReportada(6.25, -75.59, 10.0, true));

        ResultadoEntregaResponseDTO res = service.registrar(10L, req);

        assertThat(res.estado()).isEqualTo("APLICADO");
        assertThat(res.duplicado()).isFalse();
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.ENTREGADO);
        assertThat(parada.getEstado()).isEqualTo(EstadoParada.ENTREGADO);
        verify(pedidos).save(pedido);
        verify(eventosRegistrados).save(any(EventoResultadoEntrega.class));
        verify(publisher).publishEvent(any(EntregaConfirmadaEvent.class));
    }

    @Test
    @DisplayName("Registra entrega fallida por cliente ausente registrando incidencia")
    void registrar_entregaFallida_registraIncidencia() {
        UUID eventId = UUID.randomUUID();
        OffsetDateTime fecha = OffsetDateTime.now().minusMinutes(3);
        ResultadoEntregaRequestDTO req = new ResultadoEntregaRequestDTO(
                ResultadoEntregaRequestDTO.Resultado.ENTREGA_FALLIDA,
                "CLIENTE_AUSENTE", "Nadie en casa", 6.25, -75.59, fecha, eventId.toString());

        Pedido pedido = Pedido.builder().id(11L).estado(EstadoPedido.EN_REPARTO).build();
        Ruta ruta = Ruta.builder().id(101L).conductorId(CONDUCTOR_ID).build();
        ParadaRuta parada = ParadaRuta.builder().id(51L).ruta(ruta).pedidoId(11L).estado(EstadoParada.PENDIENTE).build();
        ruta.getParadas().add(parada);

        when(eventosRegistrados.findById(any())).thenReturn(Optional.empty());
        when(pedidos.findByIdForUpdate(11L)).thenReturn(Optional.of(pedido));
        when(paradas.findByPedidoIdAndEstado(11L, EstadoParada.PENDIENTE)).thenReturn(Optional.of(parada));
        when(ubicaciones.evaluar(anyDouble(), anyDouble(), any())).thenReturn(new UbicacionReportada(6.25, -75.59, 10.0, true));
        when(incidencias.save(any(IncidenciaPedido.class))).thenAnswer(i -> i.getArgument(0));

        ResultadoEntregaResponseDTO res = service.registrar(11L, req);

        assertThat(res.estado()).isEqualTo("APLICADO");
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.ENTREGA_FALLIDA);
        verify(incidencias).save(any(IncidenciaPedido.class));
        verify(publisher).publishEvent(any(IncidenciaRegistradaEvent.class));
    }

    @Test
    @DisplayName("Registra devolución por paquete rechazado cancelando la parada y publicando DevolucionIniciadaEvent")
    void registrar_paqueteRechazado_iniciaDevolucion() {
        UUID eventId = UUID.randomUUID();
        OffsetDateTime fecha = OffsetDateTime.now().minusMinutes(2);
        ResultadoEntregaRequestDTO req = new ResultadoEntregaRequestDTO(
                ResultadoEntregaRequestDTO.Resultado.DEVOLUCION_AL_REMITENTE,
                "PAQUETE_RECHAZADO", "Destinatario no lo quiere", 6.25, -75.59, fecha, eventId.toString());

        Pedido pedido = Pedido.builder().id(12L).estado(EstadoPedido.EN_REPARTO).build();
        Ruta ruta = Ruta.builder().id(102L).conductorId(CONDUCTOR_ID).build();
        ParadaRuta parada = ParadaRuta.builder().id(52L).ruta(ruta).pedidoId(12L).estado(EstadoParada.PENDIENTE).build();
        ruta.getParadas().add(parada);

        when(eventosRegistrados.findById(any())).thenReturn(Optional.empty());
        when(pedidos.findByIdForUpdate(12L)).thenReturn(Optional.of(pedido));
        when(paradas.findByPedidoIdAndEstado(12L, EstadoParada.PENDIENTE)).thenReturn(Optional.of(parada));
        when(ubicaciones.evaluar(anyDouble(), anyDouble(), any())).thenReturn(new UbicacionReportada(6.25, -75.59, 10.0, true));
        when(incidencias.save(any(IncidenciaPedido.class))).thenAnswer(i -> i.getArgument(0));

        ResultadoEntregaResponseDTO res = service.registrar(12L, req);

        assertThat(res.estado()).isEqualTo("APLICADO");
        assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.DEVOLUCION_AL_REMITENTE);
        assertThat(parada.getEstado()).isEqualTo(EstadoParada.CANCELADA);
        verify(publisher).publishEvent(any(DevolucionIniciadaEvent.class));
    }
}

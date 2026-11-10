package com.udea.demo.rutas.application.service;

import com.udea.demo.pedidos.application.dto.HistorialPedidoResponseDTO;
import com.udea.demo.pedidos.application.dto.PedidoResponseDTO;
import com.udea.demo.pedidos.domain.model.EstadoPedido;
import com.udea.demo.pedidos.domain.model.Prioridad;
import com.udea.demo.pedidos.domain.model.TipoServicio;
import com.udea.demo.pedidos.interfaces.services.PedidoServiceI;
import com.udea.demo.rutas.application.dto.*;
import com.udea.demo.rutas.domain.model.EstadoParada;
import com.udea.demo.rutas.domain.model.ParadaRuta;
import com.udea.demo.rutas.domain.model.Ruta;
import com.udea.demo.rutas.interfaces.persistence.RutaRepository;
import com.udea.demo.usuarios.application.service.ActorAuthorizationService;
import com.udea.demo.usuarios.domain.model.Conductor;
import com.udea.demo.usuarios.domain.model.Usuario;
import com.udea.demo.usuarios.interfaces.persistence.ConductorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PanelConductorService - panel operativo para conductores")
class PanelConductorServiceTest {

    @Mock private RutaRepository rutas;
    @Mock private ConductorRepository conductores;
    @Mock private PedidoServiceI pedidos;
    @Mock private ActorAuthorizationService actores;
    @Mock private com.udea.demo.rutas.interfaces.persistence.VerificacionEdadLogRepository verificacionesEdad;

    @InjectMocks
    private PanelConductorService service;

    private static final Long USUARIO_CONDUCTOR_ID = 5L;
    private static final Long CONDUCTOR_ID = 10L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "zonaHoraria", "America/Bogota");
    }

    private PedidoResponseDTO crearPedidoDTO(Long id, String tracking, EstadoPedido estado) {
        return new PedidoResponseDTO(
                id, "PED-" + id, 1L, "Dir Origen", "Dir Destino", "Paquete",
                2.5, 20.0, 15.0, 10.0, TipoServicio.ESTANDAR, Prioridad.MEDIA, Prioridad.ALTA,
                estado, null, null, LocalDateTime.now(), LocalDateTime.now(), tracking,
                LocalDateTime.now(), true, LocalDateTime.now(), "Destinatario", "300123", null,
                "Medellin", "Medellin", "05001", "05002", "Remitente", "rem@test.com", "311111",
                LocalDateTime.now().plusDays(2), "Acceso facil", null);
    }

    @Nested
    @DisplayName("Cuando no hay ruta asignada para hoy")
    class SinRutaHoy {

        @Test
        @DisplayName("Retorna DTOs vacíos o nulos si conductor o ruta no existen")
        void sinRuta_retornaValoresVacios() {
            when(actores.conductorActualUsuarioId()).thenReturn(USUARIO_CONDUCTOR_ID);
            when(conductores.findByUsuarioId(USUARIO_CONDUCTOR_ID)).thenReturn(Optional.empty());

            assertThat(service.ruta().paradas()).isEmpty();
            assertThat(service.progreso().totalEntregas()).isZero();
            assertThat(service.entregasAsignadas()).isEmpty();
            assertThat(service.siguienteParada()).isNull();
        }

        @Test
        @DisplayName("Lanza AccessDeniedException al consultar detalleEntrega sin ruta")
        void detalleEntrega_sinRuta_lanzaAccessDenied() {
            when(actores.conductorActualUsuarioId()).thenReturn(USUARIO_CONDUCTOR_ID);
            when(conductores.findByUsuarioId(USUARIO_CONDUCTOR_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.detalleEntrega(100L))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("Con ruta asignada para hoy")
    class ConRutaHoy {

        private Ruta ruta;
        private Conductor conductor;

        @BeforeEach
        void initRuta() {
            conductor = Conductor.builder().id(CONDUCTOR_ID).usuario(Usuario.builder().id(USUARIO_CONDUCTOR_ID).build()).build();
            ruta = Ruta.builder().id(1L).conductorId(CONDUCTOR_ID).fecha(LocalDate.now()).build();

            ParadaRuta p1 = ParadaRuta.builder().id(11L).ruta(ruta).pedidoId(101L).orden(1).estado(EstadoParada.ENTREGADO).build();
            ParadaRuta p2 = ParadaRuta.builder().id(12L).ruta(ruta).pedidoId(102L).orden(2).estado(EstadoParada.PENDIENTE).build();
            ParadaRuta p3 = ParadaRuta.builder().id(13L).ruta(ruta).pedidoId(103L).orden(3).estado(EstadoParada.PENDIENTE).build();
            ParadaRuta p4 = ParadaRuta.builder().id(14L).ruta(ruta).pedidoId(104L).orden(4).estado(EstadoParada.CANCELADA).build();

            ruta.getParadas().addAll(List.of(p1, p2, p3, p4));

            when(actores.conductorActualUsuarioId()).thenReturn(USUARIO_CONDUCTOR_ID);
            when(conductores.findByUsuarioId(USUARIO_CONDUCTOR_ID)).thenReturn(Optional.of(conductor));
            when(rutas.findByConductorIdAndFecha(eq(CONDUCTOR_ID), any(LocalDate.class))).thenReturn(Optional.of(ruta));
        }

        @Test
        @DisplayName("progreso calcula métricas correctamente clasificando fallidas y canceladas")
        void progreso_calculaMetricas() {
            PedidoResponseDTO ped1 = crearPedidoDTO(101L, "LT101", EstadoPedido.ENTREGADO);
            PedidoResponseDTO ped2 = crearPedidoDTO(102L, "LT102", EstadoPedido.EN_REPARTO);
            PedidoResponseDTO ped3 = crearPedidoDTO(103L, "LT103", EstadoPedido.DEVOLUCION_AL_REMITENTE);

            when(pedidos.obtenerPorIds(any())).thenReturn(Map.of(101L, ped1, 102L, ped2, 103L, ped3));

            ProgresoPanelDTO prog = service.progreso();

            assertThat(prog.entregadas()).isEqualTo(1);
            assertThat(prog.pendientes()).isEqualTo(1);
            assertThat(prog.fallidas()).isEqualTo(1); // ped3 con DEVOLUCION_AL_REMITENTE cuenta como fallida
            assertThat(prog.canceladas()).isEqualTo(1);
            assertThat(prog.totalEntregas()).isEqualTo(3);
        }

        @Test
        @DisplayName("entregasAsignadas lista paradas no canceladas o en devolucion al remitente")
        void entregasAsignadas_listaParadas() {
            PedidoResponseDTO ped1 = crearPedidoDTO(101L, "LT101", EstadoPedido.ENTREGADO);
            PedidoResponseDTO ped2 = crearPedidoDTO(102L, "LT102", EstadoPedido.EN_REPARTO);
            PedidoResponseDTO ped3 = crearPedidoDTO(103L, "LT103", EstadoPedido.DEVOLUCION_AL_REMITENTE);

            when(pedidos.obtenerPorIds(any())).thenReturn(Map.of(101L, ped1, 102L, ped2, 103L, ped3));

            List<ParadaPanelDTO> lista = service.entregasAsignadas();

            assertThat(lista).hasSize(3);
            assertThat(lista.get(0).pedidoId()).isEqualTo(101L);
            assertThat(lista.get(1).pedidoId()).isEqualTo(102L);
            assertThat(lista.get(2).pedidoId()).isEqualTo(103L);
        }

        @Test
        @DisplayName("siguienteParada retorna la primera parada pendiente no fallida")
        void siguienteParada_retornaPendiente() {
            PedidoResponseDTO ped1 = crearPedidoDTO(101L, "LT101", EstadoPedido.ENTREGADO);
            PedidoResponseDTO ped2 = crearPedidoDTO(102L, "LT102", EstadoPedido.EN_REPARTO);
            PedidoResponseDTO ped3 = crearPedidoDTO(103L, "LT103", EstadoPedido.DEVOLUCION_AL_REMITENTE);

            when(pedidos.obtenerPorIds(any())).thenReturn(Map.of(101L, ped1, 102L, ped2, 103L, ped3));
            when(pedidos.obtenerParaConductor(102L)).thenReturn(ped2);

            SiguienteParadaDTO sig = service.siguienteParada();

            assertThat(sig).isNotNull();
            assertThat(sig.pedidoId()).isEqualTo(102L);
            assertThat(sig.numeroTracking()).isEqualTo("LT102");
        }

        @Test
        @DisplayName("detalleEntrega obtiene detalle con historial de los últimos 5 eventos")
        void detalleEntrega_exitoso() {
            PedidoResponseDTO ped2 = crearPedidoDTO(102L, "LT102", EstadoPedido.EN_REPARTO);
            when(pedidos.obtenerParaConductor(102L)).thenReturn(ped2);

            List<HistorialPedidoResponseDTO> hist = List.of(
                    new HistorialPedidoResponseDTO(1L, 1L, "CREACION", "estado", "Creado", LocalDateTime.now().minusHours(3)),
                    new HistorialPedidoResponseDTO(2L, 1L, "CHECKPOINT", "estado", "En transito", LocalDateTime.now().minusHours(2)),
                    new HistorialPedidoResponseDTO(3L, 1L, "CHECKPOINT", "estado", "En reparto", LocalDateTime.now().minusHours(1))
            );
            when(pedidos.historialParaConductor(102L)).thenReturn(hist);

            DetalleEntregaDTO det = service.detalleEntrega(102L);

            assertThat(det.pedidoId()).isEqualTo(102L);
            assertThat(det.numeroTracking()).isEqualTo("LT102");
            assertThat(det.ultimosEventos()).hasSize(3);
        }

        @Test
        @DisplayName("ruta retorna paradas y marca la siguiente parada pendiente")
        void ruta_retornaRutaConSiguiente() {
            PedidoResponseDTO ped1 = crearPedidoDTO(101L, "LT101", EstadoPedido.ENTREGADO);
            PedidoResponseDTO ped2 = crearPedidoDTO(102L, "LT102", EstadoPedido.EN_REPARTO);
            PedidoResponseDTO ped3 = crearPedidoDTO(103L, "LT103", EstadoPedido.DEVOLUCION_AL_REMITENTE);

            when(pedidos.obtenerPorIds(any())).thenReturn(Map.of(101L, ped1, 102L, ped2, 103L, ped3));

            RutaPanelConductorDTO res = service.ruta();

            assertThat(res.paradas()).hasSize(4);
            assertThat(res.siguiente()).isNotNull();
            assertThat(res.siguiente().pedidoId()).isEqualTo(102L);
        }
    }
}

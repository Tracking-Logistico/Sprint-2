package com.udea.demo.pedidos.application.service;

import com.udea.demo.pedidos.application.dto.RegistrarIncidenciaRequestDTO;
import com.udea.demo.pedidos.application.dto.RegistroIncidenciaResponseDTO;
import com.udea.demo.pedidos.domain.event.DevolucionIniciadaEvent;
import com.udea.demo.pedidos.domain.event.IncidenciaRegistradaEvent;
import com.udea.demo.pedidos.domain.event.VerificacionDireccionSolicitadaEvent;
import com.udea.demo.pedidos.domain.exception.*;
import com.udea.demo.pedidos.domain.model.*;
import com.udea.demo.pedidos.domain.service.CalendarioHabil;
import com.udea.demo.pedidos.domain.service.ValidadorUbicacion;
import com.udea.demo.pedidos.interfaces.persistence.IncidenciaPedidoRepository;
import com.udea.demo.pedidos.interfaces.persistence.PedidoRepository;
import com.udea.demo.usuarios.application.service.ActorAuthorizationService;
import com.udea.demo.usuarios.domain.model.Rol;
import com.udea.demo.usuarios.domain.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HU-07 IncidenciaService - registro y gestión de incidencias")
class IncidenciaServiceTest {
    private static final Long PEDIDO_ID = 1L;
    private static final Long OPERADOR_USUARIO = 50L;

    @Mock private PedidoRepository pedidos;
    @Mock private IncidenciaPedidoRepository incidencias;
    @Mock private RegistroHistorialPedido historial;
    @Mock private ActorAuthorizationService actores;
    @Mock private ApplicationEventPublisher eventos;

    private IncidenciaService service;
    private Pedido pedido;

    @BeforeEach
    void setUp() {
        service = new IncidenciaService(pedidos, incidencias, historial, actores, new ValidadorUbicacion(100),
                new CalendarioHabil(), eventos, 3, 3);
        pedido = Pedido.builder().id(PEDIDO_ID).clienteId(10L).numeroTracking("LT1").estado(EstadoPedido.EN_REPARTO)
                .version(4L).build();
        lenient().when(actores.actorActual()).thenReturn(Usuario.builder().id(OPERADOR_USUARIO).rol(Rol.OPERADOR).build());
        lenient().when(pedidos.findByIdForUpdate(PEDIDO_ID)).thenAnswer(i -> Optional.of(pedido));
        lenient().when(incidencias.save(any(IncidenciaPedido.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static RegistrarIncidenciaRequestDTO request(String tipo, String comentario) {
        return new RegistrarIncidenciaRequestDTO(tipo, comentario, 6.2442, -75.5812, 10.0, null);
    }

    private <T> List<T> eventosPublicados(Class<T> tipo) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventos, atLeast(0)).publishEvent(captor.capture());
        return captor.getAllValues().stream().filter(tipo::isInstance).map(tipo::cast).toList();
    }

    @Nested
    @DisplayName("Criterio 1: registro de una incidencia")
    class Registro {

        @Test
        @DisplayName("Registra la incidencia con fecha, ubicación y usuario, y la agrega al historial")
        void registraIncidencia() {
            RegistroIncidenciaResponseDTO r = service.registrar(PEDIDO_ID, request("RETRASO_OPERATIVO", null));

            assertThat(r.incidencia().tipo()).isEqualTo(TipoIncidencia.RETRASO_OPERATIVO);
            assertThat(r.incidencia().reportadoPorUsuarioId()).isEqualTo(OPERADOR_USUARIO);
            assertThat(r.incidencia().latitud()).isEqualTo(6.2442);
            assertThat(r.incidencia().fecha()).isNotNull();
            assertThat(r.incidencia().fecha().getOffset()).isEqualTo(java.time.ZoneOffset.UTC);
            assertThat(r.incidencia().fecha().toInstant()).isCloseTo(java.time.Instant.now(),
                    org.assertj.core.api.Assertions.within(5, java.time.temporal.ChronoUnit.SECONDS));
            verify(actores).operadorActualId();
            verify(pedidos).saveAndFlush(pedido);
            verify(historial).registrar(eq(PEDIDO_ID), eq(OPERADOR_USUARIO), eq(RegistroHistorialPedido.INCIDENCIA),
                    isNull(), eq("RETRASO_OPERATIVO"), any(LocalDateTime.class));
            verify(historial, never()).registrar(any(), any(), eq(RegistroHistorialPedido.ESTADO_LOGISTICO), any(), any(), any());
        }

        @Test
        @DisplayName("Rechaza un tipo que no pertenece al catálogo")
        void tipoInvalido() {
            assertThatThrownBy(() -> service.registrar(PEDIDO_ID, request("PERDIDO_EN_EL_MAR", null)))
                    .isInstanceOf(TipoIncidenciaInvalidoException.class);
            verifyNoInteractions(incidencias, eventos);
        }

        @Test
        @DisplayName("Exige comentario cuando el tipo es 'Otro'")
        void comentarioObligatorioOtro() {
            assertThatThrownBy(() -> service.registrar(PEDIDO_ID, request("OTRO", "   ")))
                    .isInstanceOf(ComentarioIncidenciaRequeridoException.class);
            verify(pedidos, never()).findByIdForUpdate(any());
        }

        @Test
        @DisplayName("Exige comentario para 'Paquete dañado' (no estándar)")
        void comentarioObligatorioDanado() {
            assertThatThrownBy(() -> service.registrar(PEDIDO_ID, request("PAQUETE_DANADO", null)))
                    .isInstanceOf(ComentarioIncidenciaRequeridoException.class);
        }

        @Test
        @DisplayName("Acepta 'Otro' con comentario")
        void otroConComentario() {
            RegistroIncidenciaResponseDTO r = service.registrar(PEDIDO_ID, request("OTRO", " Cliente pidió dejarlo en portería "));

            assertThat(r.incidencia().comentario()).isEqualTo("Cliente pidió dejarlo en portería");
        }

        @Test
        @DisplayName("Rechaza coordenadas inválidas")
        void ubicacionInvalida() {
            assertThatThrownBy(() -> service.registrar(PEDIDO_ID,
                    new RegistrarIncidenciaRequestDTO("RETRASO_OPERATIVO", null, 120.0, 0.0, 5.0, null)))
                    .isInstanceOf(UbicacionInvalidaException.class);
        }

        @Test
        @DisplayName("Actualiza el estado según el tipo y lo registra en el historial")
        void actualizaEstado() {
            RegistroIncidenciaResponseDTO r = service.registrar(PEDIDO_ID, request("CLIENTE_AUSENTE", null));

            assertThat(r.estadoPedido()).isEqualTo(EstadoPedido.ENTREGA_FALLIDA);
            assertThat(r.incidencia().estadoAnterior()).isEqualTo(EstadoPedido.EN_REPARTO);
            assertThat(r.incidencia().numeroIntento()).isEqualTo(1);
            verify(historial).registrar(eq(PEDIDO_ID), eq(OPERADOR_USUARIO), eq(RegistroHistorialPedido.ESTADO_LOGISTICO),
                    eq(RegistroHistorialPedido.INCIDENCIA), eq("ENTREGA_FALLIDA"), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("Envío inexistente responde no encontrado")
        void envioNoEncontrado() {
            when(pedidos.findByIdForUpdate(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.registrar(99L, request("RETRASO_OPERATIVO", null)))
                    .isInstanceOf(PedidoNoEncontradoException.class);
        }

        @Test
        @DisplayName("Publica IncidenciaRegistrada con mensaje para el cliente")
        void publicaEvento() {
            service.registrar(PEDIDO_ID, request("RETRASO_OPERATIVO", null));

            List<IncidenciaRegistradaEvent> publicados = eventosPublicados(IncidenciaRegistradaEvent.class);
            assertThat(publicados).hasSize(1);
            assertThat(publicados.get(0).routingKey()).isEqualTo("pedido.incidencia.registrada");
            assertThat(publicados.get(0).mensajeCliente()).isEqualTo(TipoIncidencia.RETRASO_OPERATIVO.mensajeCliente());
            assertThat(publicados.get(0).clienteId()).isEqualTo(10L);
        }
    }

    @Nested
    @DisplayName("Criterios 4, 5 y 6: dirección incorrecta, rechazo e intentos")
    class Flujos {

        @Test
        @DisplayName("Dirección incorrecta: Dirección por verificar, plazo de 3 días hábiles y notificación al cliente")
        void direccionIncorrecta() {
            LocalDateTime antes = LocalDateTime.now();

            RegistroIncidenciaResponseDTO r = service.registrar(PEDIDO_ID, request("DIRECCION_INCORRECTA", null));

            assertThat(r.estadoPedido()).isEqualTo(EstadoPedido.DIRECCION_POR_VERIFICAR);
            assertThat(pedido.getFechaLimiteVerificacionDireccion())
                    .isAfterOrEqualTo(new CalendarioHabil().sumarDiasHabiles(antes, 3));
            List<VerificacionDireccionSolicitadaEvent> ev = eventosPublicados(VerificacionDireccionSolicitadaEvent.class);
            assertThat(ev).hasSize(1);
            assertThat(ev.get(0).fechaLimite()).isEqualTo(pedido.getFechaLimiteVerificacionDireccion());
            assertThat(ev.get(0).routingKey()).isEqualTo("pedido.direccion.verificacion-solicitada");
        }

        @Test
        @DisplayName("Paquete rechazado: Devolución al remitente y notificación del proceso de devolución")
        void paqueteRechazado() {
            RegistroIncidenciaResponseDTO r = service.registrar(PEDIDO_ID, request("PAQUETE_RECHAZADO", null));

            assertThat(r.estadoPedido()).isEqualTo(EstadoPedido.DEVOLUCION_AL_REMITENTE);
            List<DevolucionIniciadaEvent> ev = eventosPublicados(DevolucionIniciadaEvent.class);
            assertThat(ev).hasSize(1);
            assertThat(ev.get(0).motivo()).isEqualTo(MotivoDevolucion.PAQUETE_RECHAZADO);
            assertThat(ev.get(0).estadoAnterior()).isEqualTo(EstadoPedido.EN_REPARTO);
        }

        @Test
        @DisplayName("Tercer intento fallido: devolución con razón MAXIMO_INTENTOS")
        void tresIntentos() {
            for (int i = 0; i < 2; i++) {
                pedido.registrarIncidencia(TipoIncidencia.CLIENTE_AUSENTE, 3, LocalDateTime.now(), null);
                pedido.reprogramarEntrega(java.time.LocalDate.now().plusDays(1), java.time.LocalDate.now(), 7);
            }

            RegistroIncidenciaResponseDTO r = service.registrar(PEDIDO_ID, request("CLIENTE_AUSENTE", null));

            assertThat(r.estadoPedido()).isEqualTo(EstadoPedido.DEVOLUCION_AL_REMITENTE);
            assertThat(r.intentosEntregaFallidos()).isEqualTo(3);
            assertThat(r.incidencia().numeroIntento()).isEqualTo(3);
            List<DevolucionIniciadaEvent> ev = eventosPublicados(DevolucionIniciadaEvent.class);
            assertThat(ev).singleElement().extracting(DevolucionIniciadaEvent::motivo)
                    .isEqualTo(MotivoDevolucion.MAXIMO_INTENTOS);
        }

        @Test
        @DisplayName("Evita un cuarto intento sobre un envío ya devuelto")
        void cuartoIntento() {
            pedido = Pedido.builder().id(PEDIDO_ID).numeroTracking("LT1").estado(EstadoPedido.DEVOLUCION_AL_REMITENTE).build();

            assertThatThrownBy(() -> service.registrar(PEDIDO_ID, request("CLIENTE_AUSENTE", null)))
                    .isInstanceOf(EnvioFinalizadoException.class);
            verifyNoInteractions(incidencias, eventos);
        }
    }

    @Nested
    @DisplayName("Criterio 7: concurrencia")
    class Concurrencia {

        @Test
        @DisplayName("Si el envío cambió desde la versión que vio el operador, se rechaza con conflicto")
        void versionDesactualizada() {
            RegistrarIncidenciaRequestDTO dto = new RegistrarIncidenciaRequestDTO("CLIENTE_AUSENTE", null, null, null, null, 3L);

            assertThatThrownBy(() -> service.registrar(PEDIDO_ID, dto)).isInstanceOf(ConflictoConcurrenciaException.class);
            assertThat(pedido.getEstado()).isEqualTo(EstadoPedido.EN_REPARTO);
            verify(pedidos, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Con la versión vigente la incidencia se procesa bajo bloqueo del envío")
        void versionVigente() {
            RegistrarIncidenciaRequestDTO dto = new RegistrarIncidenciaRequestDTO("RETRASO_OPERATIVO", null, null, null, null, 4L);

            service.registrar(PEDIDO_ID, dto);

            verify(pedidos).findByIdForUpdate(PEDIDO_ID);
            verify(pedidos).saveAndFlush(pedido);
        }
    }

    @Test
    @DisplayName("El catálogo expone todos los tipos con su regla de comentario")
    void catalogo() {
        assertThat(service.catalogo()).hasSize(TipoIncidencia.values().length)
                .anyMatch(t -> t.codigo().equals("OTRO") && t.requiereComentario());
    }

    @Test
    @DisplayName("Lista las incidencias del envío con su versión actual")
    void listar() {
        when(pedidos.findById(PEDIDO_ID)).thenReturn(Optional.of(pedido));
        when(incidencias.findByPedidoIdOrderByFechaAscIdAsc(PEDIDO_ID)).thenReturn(List.of(new IncidenciaPedido(PEDIDO_ID,
                OPERADOR_USUARIO, TipoIncidencia.RETRASO_OPERATIVO, null, new UbicacionReportada(null, null, null, false),
                EstadoPedido.EN_REPARTO, EstadoPedido.EN_REPARTO, null, LocalDateTime.now())));

        var r = service.listar(PEDIDO_ID);

        assertThat(r.versionPedido()).isEqualTo(4L);
        assertThat(r.incidencias()).hasSize(1);
    }
}

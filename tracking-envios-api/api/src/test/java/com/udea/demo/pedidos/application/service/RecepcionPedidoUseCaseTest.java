package com.udea.demo.pedidos.application.service;

import com.udea.demo.pedidos.application.dto.PedidoResponseDTO;
import com.udea.demo.pedidos.application.dto.RecibirPedidoRequestDTO;
import com.udea.demo.pedidos.domain.model.EstadoPedido;
import com.udea.demo.pedidos.domain.model.Pedido;
import com.udea.demo.pedidos.domain.model.Prioridad;
import com.udea.demo.pedidos.domain.model.TipoServicio;
import com.udea.demo.pedidos.domain.service.PrioridadStrategy;
import com.udea.demo.pedidos.interfaces.persistence.PedidoRepository;
import com.udea.demo.usuarios.application.service.ActorAuthorizationService;
import com.udea.demo.usuarios.domain.model.Rol;
import com.udea.demo.usuarios.domain.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecepcionPedidoUseCase - recepción y corrección con datos tipados")
class RecepcionPedidoUseCaseTest {

    @Mock private PedidoRepository pedidos;
    @Mock private PrioridadStrategy prioridadStrategy;
    @Mock private GeneradorNumeroPedido numerosPedido;
    @Mock private ActorAuthorizationService actores;
    @Mock private LimitesServicioService limites;
    @Mock private RegistroHistorialPedido historial;

    private RecepcionPedidoUseCase useCase;
    private final PedidoMapeador mapeador = new PedidoMapeador();

    private static final String NUMERO_PEDIDO = "PED-20261015-ABCD1234";

    @BeforeEach
    void setUp() {
        useCase = new RecepcionPedidoUseCase(pedidos, prioridadStrategy, numerosPedido,
                actores, limites, historial, mapeador);

        Usuario usuario = Usuario.builder().id(1L).nombre("Juan Pérez").email("juan@ejemplo.com")
                .rol(Rol.CLIENTE).activo(true).build();
        org.mockito.Mockito.lenient().when(actores.actorActual()).thenReturn(usuario);
        org.mockito.Mockito.lenient().when(actores.clienteActualId()).thenReturn(10L);
    }

    private RecibirPedidoRequestDTO crearRequest(Double peso, TipoServicio tipo) {
        return new RecibirPedidoRequestDTO(
                "Carrera 7 #71-21, Bogotá", "Bogotá", "110111",
                "Calle 45 #12-30, Bogotá", "Bogotá", "110111",
                "Caja de documentos", peso, 25.0, 15.0, 10.0,
                tipo, "Ana María Torres", "+573001234567",
                "+573109876543");
    }

    @Test
    @DisplayName("recibir() valida límites, calcula prioridad, persiste pedido y genera evento en historial")
    void recibir_exitoso() {
        RecibirPedidoRequestDTO dto = crearRequest(2.5, TipoServicio.EXPRESS);
        when(prioridadStrategy.sugerir(TipoServicio.EXPRESS, 2.5)).thenReturn(Prioridad.ALTA);
        when(numerosPedido.generar()).thenReturn(NUMERO_PEDIDO);
        when(pedidos.existsByNumeroPedido(NUMERO_PEDIDO)).thenReturn(false);
        when(pedidos.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(p, "id", 100L);
            return p;
        });

        PedidoResponseDTO resultado = useCase.recibir(dto);

        verify(limites).validar(dto);
        verify(pedidos).save(any(Pedido.class));
        verify(historial).registrar(eq(100L), eq(1L), eq("PEDIDO_SOLICITADO"), org.mockito.ArgumentMatchers.isNull(), eq("Pedido creado por el cliente"), any());

        assertThat(resultado.id()).isEqualTo(100L);
        assertThat(resultado.numeroPedido()).isEqualTo(NUMERO_PEDIDO);
        assertThat(resultado.estado()).isEqualTo(EstadoPedido.SOLICITADO);
        assertThat(resultado.prioridadSugerida()).isEqualTo(Prioridad.ALTA);
    }

    @Test
    @DisplayName("corregir() actualiza datos del pedido en estado CORRECCION_SOLICITADA")
    void corregir_exitoso() {
        Pedido pedidoExistente = Pedido.recibir(
                10L, "Dir Vieja", "Bogotá", "110111",
                "Destino Viejo", "Bogotá", "110111", "Caja",
                1.0, 10.0, 10.0, 10.0, TipoServicio.ESTANDAR,
                NUMERO_PEDIDO, Prioridad.MEDIA, "Dest", "+573001234567",
                "Juan Pérez", "juan@ejemplo.com", "+573109876543");
        org.springframework.test.util.ReflectionTestUtils.setField(pedidoExistente, "id", 100L);
        pedidoExistente.solicitarCorreccion("Dirección incompleta", 99L);

        when(pedidos.findByIdForUpdate(100L)).thenReturn(Optional.of(pedidoExistente));
        when(prioridadStrategy.sugerir(any(), any())).thenReturn(Prioridad.MEDIA);
        when(pedidos.save(pedidoExistente)).thenReturn(pedidoExistente);

        RecibirPedidoRequestDTO dtoCorregido = crearRequest(3.0, TipoServicio.ESTANDAR);
        PedidoResponseDTO resultado = useCase.corregir(100L, dtoCorregido);

        verify(limites).validar(dtoCorregido);
        verify(pedidos).save(pedidoExistente);
        assertThat(resultado.estado()).isEqualTo(EstadoPedido.SOLICITADO);
        assertThat(resultado.pesoKg()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("corregir() de cliente ajeno lanza AccessDeniedException")
    void corregir_clienteAjeno_lanzaExcepcion() {
        Pedido pedidoAjeno = Pedido.recibir(
                999L, "Dir", "Bogotá", "110111",
                "Destino", "Bogotá", "110111", "Caja",
                1.0, 10.0, 10.0, 10.0, TipoServicio.ESTANDAR,
                NUMERO_PEDIDO, Prioridad.MEDIA, "Dest", "+573001234567",
                "Otro Cliente", "otro@ejemplo.com", "+573109876543");
        when(pedidos.findByIdForUpdate(100L)).thenReturn(Optional.of(pedidoAjeno));

        RecibirPedidoRequestDTO dto = crearRequest(2.0, TipoServicio.ESTANDAR);

        assertThatThrownBy(() -> useCase.corregir(100L, dto))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("no pertenece al cliente");
    }

    @Test
    @DisplayName("HU-13 anti-enumeración: corregir() de un pedido inexistente responde igual que uno ajeno (403)")
    void corregir_inexistente_lanzaExcepcion() {
        when(pedidos.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        RecibirPedidoRequestDTO dto = crearRequest(2.0, TipoServicio.ESTANDAR);

        assertThatThrownBy(() -> useCase.corregir(999L, dto))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessage("El pedido no pertenece al cliente autenticado");
    }
}

package com.udea.demo.pedidos.application.service;

import com.udea.demo.pedidos.application.dto.*;
import com.udea.demo.pedidos.domain.event.DevolucionIniciadaEvent;
import com.udea.demo.pedidos.domain.event.IncidenciaRegistradaEvent;
import com.udea.demo.pedidos.domain.event.VerificacionDireccionSolicitadaEvent;
import com.udea.demo.pedidos.domain.exception.ComentarioIncidenciaRequeridoException;
import com.udea.demo.pedidos.domain.exception.ConflictoConcurrenciaException;
import com.udea.demo.pedidos.domain.exception.PedidoNoEncontradoException;
import com.udea.demo.pedidos.domain.model.*;
import com.udea.demo.pedidos.domain.service.CalendarioHabil;
import com.udea.demo.pedidos.domain.service.ValidadorUbicacion;
import com.udea.demo.pedidos.interfaces.persistence.IncidenciaPedidoRepository;
import com.udea.demo.pedidos.interfaces.persistence.PedidoRepository;
import com.udea.demo.pedidos.interfaces.services.IncidenciaServiceI;
import com.udea.demo.usuarios.application.service.ActorAuthorizationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

@Service
public class IncidenciaService implements IncidenciaServiceI {
    private final PedidoRepository pedidos;
    private final IncidenciaPedidoRepository incidencias;
    private final RegistroHistorialPedido historial;
    private final ActorAuthorizationService actores;
    private final ValidadorUbicacion validadorUbicacion;
    private final CalendarioHabil calendario;
    private final ApplicationEventPublisher eventos;
    private final int maxIntentos;
    private final int plazoDireccionDiasHabiles;

    public IncidenciaService(PedidoRepository pedidos, IncidenciaPedidoRepository incidencias,
                             RegistroHistorialPedido historial, ActorAuthorizationService actores,
                             ValidadorUbicacion validadorUbicacion, CalendarioHabil calendario,
                             ApplicationEventPublisher eventos,
                             @Value("${app.incidencias.max-intentos-entrega:3}") int maxIntentos,
                             @Value("${app.incidencias.plazo-verificacion-direccion-dias-habiles:3}") int plazoDireccionDiasHabiles) {
        this.pedidos = pedidos;
        this.incidencias = incidencias;
        this.historial = historial;
        this.actores = actores;
        this.validadorUbicacion = validadorUbicacion;
        this.calendario = calendario;
        this.eventos = eventos;
        this.maxIntentos = maxIntentos;
        this.plazoDireccionDiasHabiles = plazoDireccionDiasHabiles;
    }

    @Override
    public List<TipoIncidenciaResponseDTO> catalogo() {
        return Arrays.stream(TipoIncidencia.values()).map(TipoIncidenciaResponseDTO::de).toList();
    }

    /**
     * El envío se bloquea (SELECT ... FOR UPDATE) durante la operación: un segundo operador espera a que la
     * primera transacción confirme y, si trabajaba con una versión anterior, recibe un conflicto.
     */
    @Override @Transactional
    public RegistroIncidenciaResponseDTO registrar(Long pedidoId, RegistrarIncidenciaRequestDTO dto) {
        actores.operadorActualId();
        Long usuario = actores.actorActual().getId();
        TipoIncidencia tipo = TipoIncidencia.desde(dto.tipo());
        String comentario = normalizar(dto.comentario());
        if (tipo.requiereComentario() && comentario == null)
            throw new ComentarioIncidenciaRequeridoException(tipo.descripcion());
        UbicacionReportada ubicacion = validadorUbicacion.evaluar(dto.latitud(), dto.longitud(), dto.precisionMetros());

        Pedido pedido = pedidos.findByIdForUpdate(pedidoId).orElseThrow(() -> new PedidoNoEncontradoException(pedidoId));
        if (dto.versionEsperada() != null && !dto.versionEsperada().equals(pedido.getVersion()))
            throw new ConflictoConcurrenciaException();

        EstadoPedido anterior = pedido.getEstado();
        LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC);
        EstadoPedido resultante = pedido.registrarIncidencia(tipo, maxIntentos, ahora,
                calendario.sumarDiasHabiles(ahora, plazoDireccionDiasHabiles));
        pedidos.saveAndFlush(pedido);

        IncidenciaPedido incidencia = incidencias.save(new IncidenciaPedido(pedidoId, usuario, tipo, comentario,
                ubicacion, anterior, resultante, tipo.cuentaComoIntento() ? pedido.getIntentosEntregaFallidos() : null, ahora));
        historial.registrar(pedidoId, usuario, RegistroHistorialPedido.INCIDENCIA, null, tipo.name(), ahora);
        if (resultante != anterior) {
            historial.registrar(pedidoId, usuario, RegistroHistorialPedido.ESTADO_LOGISTICO,
                    RegistroHistorialPedido.INCIDENCIA, resultante.name(), ahora);
        }
        publicarEventos(pedido, incidencia, anterior);
        return new RegistroIncidenciaResponseDTO(IncidenciaResponseDTO.de(incidencia), pedido.getEstado(),
                pedido.getVersion(), pedido.getIntentosEntregaFallidos());
    }

    @Override @Transactional(readOnly = true)
    public IncidenciasPedidoResponseDTO listar(Long pedidoId) {
        actores.operadorActualId();
        Pedido pedido = pedidos.findById(pedidoId).orElseThrow(() -> new PedidoNoEncontradoException(pedidoId));
        return new IncidenciasPedidoResponseDTO(pedidoId, pedido.getEstado(), pedido.getVersion(),
                pedido.getIntentosEntregaFallidos(), incidencias.findByPedidoIdOrderByFechaAscIdAsc(pedidoId).stream()
                .map(IncidenciaResponseDTO::de).toList());
    }

    private void publicarEventos(Pedido pedido, IncidenciaPedido incidencia, EstadoPedido anterior) {
        eventos.publishEvent(IncidenciaRegistradaEvent.de(pedido, incidencia));
        if (pedido.getEstado() == EstadoPedido.DIRECCION_POR_VERIFICAR) {
            eventos.publishEvent(VerificacionDireccionSolicitadaEvent.de(pedido));
        } else if (pedido.getEstado() == EstadoPedido.DEVOLUCION_AL_REMITENTE) {
            MotivoDevolucion motivo = incidencia.getTipo() == TipoIncidencia.PAQUETE_RECHAZADO
                    ? MotivoDevolucion.PAQUETE_RECHAZADO : MotivoDevolucion.MAXIMO_INTENTOS;
            eventos.publishEvent(DevolucionIniciadaEvent.de(pedido, anterior, motivo));
        }
    }

    private static String normalizar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}

package com.udea.demo.rutas.application.service;

import com.udea.demo.pedidos.application.service.RegistroHistorialPedido;
import com.udea.demo.pedidos.domain.event.DevolucionIniciadaEvent;
import com.udea.demo.pedidos.domain.model.EstadoPedido;
import com.udea.demo.pedidos.domain.model.MotivoDevolucion;
import com.udea.demo.pedidos.domain.model.OrigenCheckpoint;
import com.udea.demo.pedidos.domain.model.Pedido;
import com.udea.demo.pedidos.interfaces.persistence.PedidoRepository;
import com.udea.demo.rutas.application.dto.VerificacionEdadRequestDTO;
import com.udea.demo.rutas.application.dto.VerificacionEdadResponseDTO;
import com.udea.demo.rutas.domain.model.EstadoParada;
import com.udea.demo.rutas.domain.model.ParadaRuta;
import com.udea.demo.rutas.domain.model.ResultadoVerificacionEdad;
import com.udea.demo.rutas.domain.model.VerificacionEdadLog;
import com.udea.demo.rutas.interfaces.persistence.ParadaRutaRepository;
import com.udea.demo.rutas.interfaces.persistence.RutaRepository;
import com.udea.demo.rutas.interfaces.persistence.VerificacionEdadLogRepository;
import com.udea.demo.usuarios.application.service.ActorAuthorizationService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * HU-13 CA-5/6/7: el conductor registra el resultado de la verificación de edad de una entrega 18+.
 *
 * <ul>
 *   <li><b>Anti-IDOR.</b> Solo el conductor de la parada PENDIENTE del envío. Un envío ajeno, reasignado o
 *       inexistente responde el mismo 403, así que no se pueden enumerar envíos. El trigger de V25 lo
 *       repite en la BD.</li>
 *   <li><b>Minimización.</b> Solo se guarda el resultado, el conductor y el instante UTC (verificacion_edad_log).</li>
 *   <li><b>Idempotencia y concurrencia.</b> El envío se bloquea (SELECT ... FOR UPDATE). Un reintento idéntico
 *       responde duplicado=true; un resultado distinto del ya registrado, 409. La PK por envío es la garantía final.</li>
 *   <li><b>CUMPLE (CA-6).</b> Habilita el cierre como ENTREGADO por el endpoint de resultado.</li>
 *   <li><b>NO_CUMPLE (CA-7).</b> El envío pasa a ENTREGA_FALLIDA_VERIFICACION_EDAD, se cancela la parada y se inicia
 *       el retorno a bodega (DevolucionIniciadaEvent, el mismo flujo de un paquete rechazado).</li>
 *   <li><b>Auditoría (regla 7).</b> Historial con usuario, fecha y origen (EN_LINEA u OFFLINE).</li>
 * </ul>
 * El log se inserta y se hace flush ANTES de cambiar el estado o la parada, porque los triggers de V25 y V27 lo exigen.
 */
@Service
public class VerificacionEdadService {
    static final String NO_ASIGNADO = "El envío no está asignado al conductor autenticado";
    static final String EVENTO_VERIFICACION = "VERIFICACION_EDAD";
    private static final Set<EstadoPedido> EN_ENTREGA = EnumSet.of(EstadoPedido.EN_REPARTO, EstadoPedido.ENTREGA_REPROGRAMADA);

    private final PedidoRepository pedidos;
    private final ParadaRutaRepository paradas;
    private final RutaRepository rutas;
    private final VerificacionEdadLogRepository verificaciones;
    private final RegistroHistorialPedido historial;
    private final ActorAuthorizationService actores;
    private final ApplicationEventPublisher publisher;

    public VerificacionEdadService(PedidoRepository pedidos, ParadaRutaRepository paradas, RutaRepository rutas,
                                   VerificacionEdadLogRepository verificaciones, RegistroHistorialPedido historial,
                                   ActorAuthorizationService actores, ApplicationEventPublisher publisher) {
        this.pedidos = pedidos;
        this.paradas = paradas;
        this.rutas = rutas;
        this.verificaciones = verificaciones;
        this.historial = historial;
        this.actores = actores;
        this.publisher = publisher;
    }

    @Transactional
    public VerificacionEdadResponseDTO registrar(Long pedidoId, VerificacionEdadRequestDTO solicitud) {
        if (solicitud == null || solicitud.resultado() == null) {
            throw new IllegalArgumentException("El resultado de la verificación es obligatorio");
        }
        Long usuarioId = actores.conductorActualUsuarioId();
        Long conductorId = actores.conductorActualId();
        OrigenCheckpoint origen = solicitud.origen() == null ? OrigenCheckpoint.EN_LINEA : solicitud.origen();

        Pedido pedido = pedidos.findByIdForUpdate(pedidoId).orElseThrow(() -> new AccessDeniedException(NO_ASIGNADO));

        Optional<VerificacionEdadLog> previa = verificaciones.findById(pedidoId);
        if (previa.isPresent()) {
            return reintento(previa.get(), usuarioId, solicitud.resultado(), pedido);
        }

        ParadaRuta parada = paradas.findByPedidoIdAndEstado(pedidoId, EstadoParada.PENDIENTE)
                .filter(p -> p.getRuta() != null && conductorId.equals(p.getRuta().getConductorId()))
                .orElseThrow(() -> new AccessDeniedException(NO_ASIGNADO));
        if (!pedido.isRequiereVerificacionEdad()) {
            throw new IllegalStateException("El envío no requiere verificación de edad");
        }
        if (!EN_ENTREGA.contains(pedido.getEstado())) {
            throw new IllegalStateException("La verificación de edad solo se registra durante la entrega");
        }

        Instant ahora = Instant.now();
        VerificacionEdadLog registro = verificaciones.saveAndFlush(
                VerificacionEdadLog.registrar(pedidoId, usuarioId, solicitud.resultado(), ahora));
        LocalDateTime fecha = LocalDateTime.now();
        historial.registrar(pedidoId, usuarioId, EVENTO_VERIFICACION, origen.name(),
                solicitud.resultado() == ResultadoVerificacionEdad.CUMPLE
                        ? "Mayor de edad verificado" : "Verificación no aprobada", fecha);

        if (solicitud.resultado() == ResultadoVerificacionEdad.NO_CUMPLE) {
            EstadoPedido anterior = pedido.getEstado();
            pedido.fallarPorVerificacionEdad(fecha);
            parada.getRuta().cancelarParada(pedidoId);
            rutas.save(parada.getRuta());
            pedidos.save(pedido);
            historial.registrar(pedidoId, usuarioId, RegistroHistorialPedido.ESTADO_LOGISTICO, "VERIFICACION_EDAD",
                    EstadoPedido.ENTREGA_FALLIDA_VERIFICACION_EDAD.name(), fecha);
            publisher.publishEvent(DevolucionIniciadaEvent.de(pedido, anterior,
                    MotivoDevolucion.VERIFICACION_EDAD_NO_APROBADA));
        }
        return respuesta(registro, pedido, false);
    }

    private VerificacionEdadResponseDTO reintento(VerificacionEdadLog previa, Long usuarioId,
                                                  ResultadoVerificacionEdad resultado, Pedido pedido) {
        if (!previa.getConductorUsuarioId().equals(usuarioId)) {
            // No se revela que exista un resultado de otro conductor.
            throw new AccessDeniedException(NO_ASIGNADO);
        }
        if (previa.getResultado() != resultado) {
            throw new IllegalStateException("La verificación de edad de este envío ya fue registrada");
        }
        return respuesta(previa, pedido, true);
    }

    private static VerificacionEdadResponseDTO respuesta(VerificacionEdadLog registro, Pedido pedido, boolean duplicado) {
        return new VerificacionEdadResponseDTO(registro.getPedidoId(), registro.getResultado(), pedido.getEstado().name(),
                registro.getFechaVerificacion(), registro.getResultado().permiteEntrega(), duplicado);
    }
}

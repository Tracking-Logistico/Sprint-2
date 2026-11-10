package com.udea.demo.rutas.application.service;

import com.udea.demo.pedidos.application.service.RegistroHistorialPedido;
import com.udea.demo.pedidos.domain.event.DevolucionIniciadaEvent;
import com.udea.demo.pedidos.domain.event.EntregaConfirmadaEvent;
import com.udea.demo.pedidos.domain.event.IncidenciaRegistradaEvent;
import com.udea.demo.pedidos.domain.model.*;
import com.udea.demo.pedidos.domain.service.ValidadorUbicacion;
import com.udea.demo.pedidos.interfaces.persistence.*;
import com.udea.demo.rutas.application.dto.*;
import com.udea.demo.rutas.domain.model.*;
import com.udea.demo.rutas.interfaces.persistence.*;
import com.udea.demo.usuarios.application.service.ActorAuthorizationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class RegistroResultadoEntregaService {
    private final EventoResultadoEntregaRepository eventosRegistrados;
    private final ParadaRutaRepository paradas;
    private final RutaRepository rutas;
    private final PedidoRepository pedidos;
    private final IncidenciaPedidoRepository incidencias;
    private final RegistroHistorialPedido historial;
    private final ValidadorUbicacion ubicaciones;
    private final ActorAuthorizationService actores;
    private final ApplicationEventPublisher publisher;
    private final int maxIntentos;
    private final VerificacionEdadLogRepository verificacionesEdad;

    public RegistroResultadoEntregaService(EventoResultadoEntregaRepository eventosRegistrados,
            ParadaRutaRepository paradas, RutaRepository rutas, PedidoRepository pedidos,
            IncidenciaPedidoRepository incidencias, RegistroHistorialPedido historial,
            ValidadorUbicacion ubicaciones, ActorAuthorizationService actores,
            ApplicationEventPublisher publisher,
            @Value("${app.incidencias.max-intentos-entrega:3}") int maxIntentos,
            VerificacionEdadLogRepository verificacionesEdad) {
        this.verificacionesEdad = verificacionesEdad;
        this.eventosRegistrados = eventosRegistrados;
        this.paradas = paradas;
        this.rutas = rutas;
        this.pedidos = pedidos;
        this.incidencias = incidencias;
        this.historial = historial;
        this.ubicaciones = ubicaciones;
        this.actores = actores;
        this.publisher = publisher;
        this.maxIntentos = maxIntentos;
    }

    /** Cada evento se procesa en su propia transacción, también durante sincronizaciones por lotes. */
    @Transactional
    public ResultadoEntregaResponseDTO registrar(Long pedidoId, ResultadoEntregaRequestDTO evento) {
        Long usuarioId = actores.conductorActualUsuarioId();
        if (evento == null || evento.resultado() == null || evento.idEventoCliente() == null
                || evento.fechaEvento() == null) throw new IllegalArgumentException("Evento incompleto");
        String id = evento.idEventoCliente().toLowerCase(Locale.ROOT);
        UUID.fromString(id);
        Optional<EventoResultadoEntrega> anterior = eventosRegistrados.findById(id);
        if (anterior.isPresent()) return respuestaDuplicada(anterior.get(), pedidoId, usuarioId, evento);

        // El bloqueo del pedido serializa intentos concurrentes sobre un mismo envío.
        Pedido pedido = pedidos.findByIdForUpdate(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("No existe el pedido"));
        anterior = eventosRegistrados.findById(id);
        if (anterior.isPresent()) return respuestaDuplicada(anterior.get(), pedidoId, usuarioId, evento);
        validarFecha(evento.fechaEvento());
        ParadaRuta parada = paradas.findByPedidoIdAndEstado(pedidoId, EstadoParada.PENDIENTE)
                .filter(p -> p.getRuta() != null)
                .orElseThrow(() -> new AccessDeniedException("No existe una parada pendiente asignada al conductor"));
        Long conductorId = actores.conductorActualId();
        if (!conductorId.equals(parada.getRuta().getConductorId()))
            throw new AccessDeniedException("La entrega pertenece a otro conductor");

        TipoIncidencia tipo = validarNovedad(evento);
        UbicacionReportada ubicacion = ubicaciones.evaluar(evento.latitud(), evento.longitud(), null);
        LocalDateTime ahora = LocalDateTime.now();
        EstadoPedido anteriorEstado = pedido.getEstado();
        if (evento.resultado() == ResultadoEntregaRequestDTO.Resultado.ENTREGADO) {
            // HU-13 CA-5: un envío 18+ no pasa a ENTREGADO sin "Mayor de edad verificado" (V25 lo repite en la BD).
            // Consultado bajo el bloqueo del envío (findByIdForUpdate): no hay carrera con la verificación.
            boolean edadVerificada = verificacionesEdad.findById(pedidoId)
                    .map(v -> v.getResultado().permiteEntrega()).orElse(false);
            pedido.entregarConVerificacionEdad(edadVerificada);
            parada.getRuta().marcarParadaEntregada(pedidoId);
            rutas.save(parada.getRuta());
            historial.registrar(pedidoId, usuarioId, RegistroHistorialPedido.ESTADO_LOGISTICO,
                    "RESULTADO_ENTREGA", "ENTREGADO", ahora);
            publisher.publishEvent(EntregaConfirmadaEvent.de(pedido));
        } else {
            EstadoPedido nuevo = pedido.registrarIncidencia(tipo, maxIntentos, ahora, ahora);
            IncidenciaPedido incidencia = incidencias.save(new IncidenciaPedido(
                    pedidoId, usuarioId, tipo, evento.motivo().trim(), ubicacion, anteriorEstado,
                    nuevo, tipo.cuentaComoIntento() ? pedido.getIntentosEntregaFallidos() : null, ahora));
            historial.registrar(pedidoId, usuarioId, RegistroHistorialPedido.INCIDENCIA,
                    "RESULTADO_ENTREGA", tipo.name() + ": " + evento.motivo().trim(), ahora);
            publisher.publishEvent(IncidenciaRegistradaEvent.de(pedido, incidencia));
            if (nuevo == EstadoPedido.DEVOLUCION_AL_REMITENTE) {
                parada.getRuta().cancelarParada(pedidoId);
                rutas.save(parada.getRuta());
                MotivoDevolucion motivo = tipo == TipoIncidencia.PAQUETE_RECHAZADO
                        ? MotivoDevolucion.PAQUETE_RECHAZADO : MotivoDevolucion.MAXIMO_INTENTOS;
                publisher.publishEvent(DevolucionIniciadaEvent.de(pedido, anteriorEstado, motivo));
            }
            if (nuevo != anteriorEstado) historial.registrar(pedidoId, usuarioId,
                    RegistroHistorialPedido.ESTADO_LOGISTICO, "RESULTADO_ENTREGA", nuevo.name(), ahora);
        }
        pedidos.save(pedido);
        eventosRegistrados.save(new EventoResultadoEntrega(id, pedidoId, usuarioId, evento));
        return new ResultadoEntregaResponseDTO(id, pedidoId, evento.resultado(), "APLICADO", evento.fechaEvento(), false);
    }

    private ResultadoEntregaResponseDTO respuestaDuplicada(EventoResultadoEntrega anterior, Long pedidoId,
            Long usuarioId, ResultadoEntregaRequestDTO evento) {
        if (!anterior.getPedidoId().equals(pedidoId) || !anterior.getUsuarioId().equals(usuarioId)
                || anterior.getResultado() != evento.resultado()
                || !Objects.equals(anterior.getCodigoNovedad(), evento.codigoNovedad())
                || !Objects.equals(anterior.getMotivo(), evento.motivo())
                || !anterior.getFechaEvento().toInstant().equals(evento.fechaEvento().toInstant()))
            throw new IllegalStateException("El identificador del evento ya corresponde a otro registro");
        return new ResultadoEntregaResponseDTO(anterior.getIdEventoCliente(), pedidoId,
                anterior.getResultado(), "DUPLICADO", anterior.getFechaEvento(), true);
    }

    private void validarFecha(OffsetDateTime fecha) {
        Instant instante = fecha.toInstant();
        if (instante.isAfter(Instant.now().plus(Duration.ofMinutes(5))))
            throw new IllegalArgumentException("La fecha del evento es posterior a la tolerancia permitida");
        if (instante.isBefore(Instant.now().minus(Duration.ofHours(24))))
            throw new IllegalArgumentException("El evento supera la ventana de sincronización de 24 horas");
    }

    private TipoIncidencia validarNovedad(ResultadoEntregaRequestDTO evento) {
        if (evento.resultado() == ResultadoEntregaRequestDTO.Resultado.ENTREGADO) {
            if (evento.codigoNovedad() != null && !evento.codigoNovedad().isBlank())
                throw new IllegalArgumentException("Una entrega exitosa no debe indicar novedad");
            return null;
        }
        if (evento.motivo() == null || evento.motivo().isBlank())
            throw new IllegalArgumentException("El motivo es obligatorio");
        TipoIncidencia tipo = TipoIncidencia.desde(evento.codigoNovedad());
        if (evento.resultado() == ResultadoEntregaRequestDTO.Resultado.ENTREGA_FALLIDA
                && tipo != TipoIncidencia.CLIENTE_AUSENTE)
            throw new IllegalArgumentException("Código de novedad incompatible con entrega fallida");
        if (evento.resultado() == ResultadoEntregaRequestDTO.Resultado.DEVOLUCION_AL_REMITENTE
                && tipo != TipoIncidencia.PAQUETE_RECHAZADO)
            throw new IllegalArgumentException("Código de novedad incompatible con devolución");
        return tipo;
    }
}

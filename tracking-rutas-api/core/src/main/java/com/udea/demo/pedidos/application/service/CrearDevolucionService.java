package com.udea.demo.pedidos.application.service;

import com.udea.demo.pedidos.domain.event.DevolucionCreadaEvent;
import com.udea.demo.pedidos.domain.event.DevolucionIniciadaEvent;
import com.udea.demo.pedidos.domain.model.Pedido;
import com.udea.demo.pedidos.interfaces.persistence.PedidoRepository;
import com.udea.demo.rutas.domain.model.EstadoParada;
import com.udea.demo.rutas.interfaces.persistence.ParadaRutaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Crea el envío de retorno a bodega a partir de un DevolucionIniciadaEvent (HU-17). En rutas lo invoca de forma
 * síncrona DevolucionIniciadaListener, dentro de la misma transacción que inició la devolución (HU-13 CA-7:
 * paquete rechazado o verificación de edad no aprobada). El estado, el historial, la parada y el retorno se
 * confirman o se revierten juntos. Es idempotente: si ya existe un retorno para el envío, lo devuelve
 * (y uk_pedidos_origen_devolucion lo garantiza en la BD).
 */
@Service
public class CrearDevolucionService {
    private final PedidoRepository pedidos;
    private final GeneradorNumeroPedido numerosPedido;
    private final GeneradorNumeroTracking numerosTracking;
    private final RegistroHistorialPedido historial;
    private final ParadaRutaRepository paradas;
    private final ApplicationEventPublisher eventos;
    private final com.udea.demo.usuarios.application.service.ActorAuthorizationService actores;
    private final String nombreBodega;
    private final String emailBodega;
    private final String telefonoBodega;
    private final String direccionBodega;
    private final String ciudadBodega;
    private final String codigoPostalBodega;

    public CrearDevolucionService(PedidoRepository pedidos, GeneradorNumeroPedido numerosPedido,
                                  GeneradorNumeroTracking numerosTracking, RegistroHistorialPedido historial,
                                  ParadaRutaRepository paradas, ApplicationEventPublisher eventos,
                                  com.udea.demo.usuarios.application.service.ActorAuthorizationService actores,
                                  @Value("${app.shipment.return.warehouse-name:Empresa logística}") String nombreBodega,
                                  @Value("${app.shipment.return.warehouse-email:}") String emailBodega,
                                  @Value("${app.shipment.return.warehouse-phone:}") String telefonoBodega,
                                  @Value("${app.shipment.return.warehouse-address:Bodega logística}") String direccionBodega,
                                  @Value("${app.shipment.return.warehouse-city:}") String ciudadBodega,
                                  @Value("${app.shipment.return.warehouse-postal-code:}") String codigoPostalBodega) {
        this.pedidos = pedidos;
        this.numerosPedido = numerosPedido;
        this.numerosTracking = numerosTracking;
        this.historial = historial;
        this.paradas = paradas;
        this.eventos = eventos;
        this.actores = actores;
        this.nombreBodega = exigirTexto(nombreBodega, "app.shipment.return.warehouse-name");
        this.emailBodega = normalizar(emailBodega);
        this.telefonoBodega = normalizar(telefonoBodega);
        this.direccionBodega = exigirTexto(direccionBodega, "app.shipment.return.warehouse-address");
        this.ciudadBodega = normalizar(ciudadBodega);
        this.codigoPostalBodega = normalizar(codigoPostalBodega);
    }

    @Transactional
    public Pedido crear(DevolucionIniciadaEvent solicitud) {
        Pedido original = pedidos.findByIdForUpdate(solicitud.pedidoId())
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró el envío original de la devolución: " + solicitud.pedidoId()));
        var existente = pedidos.findByPedidoOrigenDevolucionId(original.getId());
        if (existente.isPresent()) return existente.get();

        Long conductorUsuarioId = paradas.findConductorUsuarioIdByPedidoIdAndEstado(
                original.getId(), EstadoParada.PENDIENTE).orElse(null);
        LocalDateTime ahora = LocalDateTime.now();
        Pedido devolucion = Pedido.crearDevolucion(original, generarNumeroPedidoUnico(),
                generarTrackingUnico(), nombreBodega, emailBodega, telefonoBodega, direccionBodega,
                ciudadBodega, codigoPostalBodega, ahora);
        Pedido guardado = pedidos.saveAndFlush(devolucion);
        Long usuarioId = usuarioActual();
        historial.registrar(original.getId(), usuarioId, "RETORNO_A_BODEGA", "TRACKING_DEVOLUCION",
                guardado.getNumeroTracking(), ahora);
        historial.registrar(guardado.getId(), usuarioId, "DEVOLUCION_CREADA", "TRACKING_ORIGEN",
                original.getNumeroTracking(), ahora);
        eventos.publishEvent(DevolucionCreadaEvent.de(guardado.getId(), guardado.getNumeroTracking(),
                original.getClienteId(), original.getId(), original.getNumeroTracking(), original.getRemitenteEmail(),
                conductorUsuarioId, "La devolución del envío " + original.getNumeroTracking()
                        + " fue creada. Nuevo código de seguimiento: " + guardado.getNumeroTracking() + "."));
        return guardado;
    }

    /** Usuario de la sesión que originó la devolución (regla 7); null si viene de una tarea programada. */
    private Long usuarioActual() {
        try {
            return actores.actorActual().getId();
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            return null;
        }
    }

    private String generarNumeroPedidoUnico() {
        for (int intento = 0; intento < 10; intento++) {
            String numero = numerosPedido.generar();
            if (!pedidos.existsByNumeroPedido(numero)) return numero;
        }
        throw new IllegalStateException("No fue posible generar un identificador único para la devolución");
    }

    private String generarTrackingUnico() {
        for (int intento = 0; intento < 10; intento++) {
            String tracking = numerosTracking.generar();
            if (!pedidos.existsByNumeroTracking(tracking)) return tracking;
        }
        throw new IllegalStateException("No fue posible generar un código de tracking único para la devolución");
    }

    private static String exigirTexto(String valor, String propiedad) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("La configuración " + propiedad + " es obligatoria");
        }
        return valor.trim();
    }

    private static String normalizar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}

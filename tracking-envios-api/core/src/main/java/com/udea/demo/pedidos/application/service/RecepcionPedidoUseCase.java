package com.udea.demo.pedidos.application.service;

import com.udea.demo.pedidos.application.dto.*;
import com.udea.demo.pedidos.domain.exception.PedidoNoEncontradoException;
import com.udea.demo.pedidos.domain.model.*;
import com.udea.demo.pedidos.domain.service.PrioridadStrategy;
import com.udea.demo.pedidos.interfaces.persistence.PedidoRepository;
import com.udea.demo.usuarios.application.service.ActorAuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class RecepcionPedidoUseCase {
    private final PedidoRepository pedidos;
    private final PrioridadStrategy prioridadStrategy;
    private final GeneradorNumeroPedido numerosPedido;
    private final ActorAuthorizationService actores;
    private final LimitesServicioService limites;
    private final RegistroHistorialPedido historial;
    private final PedidoMapeador mapeador;

    public RecepcionPedidoUseCase(PedidoRepository pedidos, PrioridadStrategy prioridadStrategy,
            GeneradorNumeroPedido numerosPedido, ActorAuthorizationService actores,
            LimitesServicioService limites, RegistroHistorialPedido historial, PedidoMapeador mapeador) {
        this.pedidos = pedidos;
        this.prioridadStrategy = prioridadStrategy;
        this.numerosPedido = numerosPedido;
        this.actores = actores;
        this.limites = limites;
        this.historial = historial;
        this.mapeador = mapeador;
    }

    @Transactional
    public PedidoResponseDTO recibir(RecibirPedidoRequestDTO dto) {
        limites.validar(dto);
        Long clienteId = actores.clienteActualId();
        var remitente = actores.actorActual();
        Prioridad sugerida = prioridadStrategy.sugerir(dto.tipoServicio(), dto.pesoKg());
        Pedido pedido = Pedido.recibir(clienteId, dto.direccionOrigen(), dto.ciudadOrigen(), dto.codigoPostalOrigen(),
                dto.direccionDestino(), dto.ciudadDestino(), dto.codigoPostalDestino(),
                dto.descripcionPaquete(), dto.pesoKg(), dto.largoCm(), dto.anchoCm(), dto.altoCm(),
                dto.tipoServicio(), generarNumeroPedidoUnico(), sugerida, dto.destinatarioNombre(), dto.destinatarioTelefono(),
                remitente.getNombre(), remitente.getEmail(), dto.remitenteTelefono(), dto.destinatarioEmail());
        // HU-13 CA-4: solo el remitente (rol CLIENTE en POST /pedidos) define la verificación de edad 18+.
        pedido.definirVerificacionEdad(Boolean.TRUE.equals(dto.requiereVerificacionEdad()));
        Pedido guardado = pedidos.save(pedido);
        evento(guardado.getId(), actores.actorActual().getId(), "PEDIDO_SOLICITADO", null,
                "Pedido creado por el cliente");
        if (guardado.isRequiereVerificacionEdad()) {
            evento(guardado.getId(), remitente.getId(), EVENTO_VERIFICACION_EDAD, CAMPO_VERIFICACION_EDAD,
                    "Verificación de edad obligatoria (18+) activada por el remitente" + origenAuditoria());
        }
        return mapeador.map(guardado);
    }

    @Transactional
    public PedidoResponseDTO corregir(Long id, RecibirPedidoRequestDTO dto) {
        limites.validar(dto);
        // HU-13 anti-IDOR/anti-enumeración: un envío inexistente o ajeno (incluido el del que se es solo destinatario)
        // responde el mismo 403, así no se distingue qué ids existen.
        Long clienteActual = actores.clienteActualId();
        Pedido p = pedidos.findByIdForUpdate(id)
                .filter(pedido -> pedido.getClienteId().equals(clienteActual))
                .orElseThrow(() -> new AccessDeniedException("El pedido no pertenece al cliente autenticado"));
        Prioridad sugerida = prioridadStrategy.sugerir(dto.tipoServicio(), dto.pesoKg());
        p.corregir(dto.direccionOrigen(), dto.ciudadOrigen(), dto.codigoPostalOrigen(), dto.direccionDestino(),
                dto.ciudadDestino(), dto.codigoPostalDestino(), dto.descripcionPaquete(), dto.pesoKg(), dto.largoCm(),
                dto.anchoCm(), dto.altoCm(), dto.tipoServicio(), dto.destinatarioNombre(), dto.destinatarioTelefono(),
                dto.remitenteTelefono(), sugerida, dto.destinatarioEmail());
        evento(id, actores.actorActual().getId(), "PEDIDO_CORREGIDO", null, "El cliente actualizó la información observada");
        // HU-13 CA-4: durante la corrección el remitente puede cambiar el flag; si no lo envía, se conserva.
        if (dto.requiereVerificacionEdad() != null && dto.requiereVerificacionEdad() != p.isRequiereVerificacionEdad()) {
            p.definirVerificacionEdad(dto.requiereVerificacionEdad());
            evento(id, actores.actorActual().getId(), EVENTO_VERIFICACION_EDAD, CAMPO_VERIFICACION_EDAD,
                    (dto.requiereVerificacionEdad() ? "Verificación de edad obligatoria (18+) activada por el remitente"
                            : "Verificación de edad obligatoria (18+) desactivada por el remitente") + origenAuditoria());
        }
        return mapeador.map(pedidos.save(p));
    }

    private String generarNumeroPedidoUnico() {
        for (int i = 0; i < 10; i++) {
            String numero = numerosPedido.generar();
            if (!pedidos.existsByNumeroPedido(numero)) return numero;
        }
        throw new IllegalStateException("No fue posible generar un identificador único de pedido");
    }

    static final String EVENTO_VERIFICACION_EDAD = "VERIFICACION_EDAD_CONFIGURADA";
    static final java.util.Set<String> ORIGENES = java.util.Set.of("WEB", "MOVIL");

    /**
     * HU-13 regla 7: origen del cambio del flag, tomado de la cabecera opcional X-Origen (lista blanca WEB|MOVIL,
     * WEB por defecto). Un valor fuera de la lista responde 400 y no se refleja.
     */
    static String origenAuditoria() {
        String origen = "WEB";
        var atributos = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (atributos instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
            String cabecera = servlet.getRequest().getHeader("X-Origen");
            if (cabecera != null && !cabecera.isBlank()) {
                origen = cabecera.strip().toUpperCase(java.util.Locale.ROOT);
                if (!ORIGENES.contains(origen)) {
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.BAD_REQUEST);
                }
            }
        }
        return " (origen " + origen + ")";
    }
    static final String CAMPO_VERIFICACION_EDAD = "requiereVerificacionEdad";

    private void evento(Long pedidoId, Long usuarioId, String tipo, String campo, String detalle) {
        historial.registrar(pedidoId, usuarioId, tipo, campo, detalle, LocalDateTime.now());
    }
}

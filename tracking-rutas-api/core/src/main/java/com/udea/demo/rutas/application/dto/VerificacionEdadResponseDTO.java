package com.udea.demo.rutas.application.dto;

import com.udea.demo.rutas.domain.model.ResultadoVerificacionEdad;

import java.time.Instant;

/**
 * HU-13: resultado registrado. {@code duplicado=true} indica un reintento idéntico ya aplicado (idempotencia).
 * {@code puedeEntregarse} solo es true con CUMPLE: con NO_CUMPLE el envío quedó en
 * ENTREGA_FALLIDA_VERIFICACION_EDAD y en retorno a bodega.
 */
public record VerificacionEdadResponseDTO(Long pedidoId, ResultadoVerificacionEdad resultado, String estadoPedido,
                                          Instant fechaVerificacion, boolean puedeEntregarse, boolean duplicado) {
}

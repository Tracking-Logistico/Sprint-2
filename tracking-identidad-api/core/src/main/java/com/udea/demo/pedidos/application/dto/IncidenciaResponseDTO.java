package com.udea.demo.pedidos.application.dto;

import com.udea.demo.pedidos.domain.model.EstadoPedido;
import com.udea.demo.pedidos.domain.model.IncidenciaPedido;
import com.udea.demo.pedidos.domain.model.TipoIncidencia;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record IncidenciaResponseDTO(
        Long id,
        Long pedidoId,
        TipoIncidencia tipo,
        String descripcionTipo,
        String comentario,
        Double latitud,
        Double longitud,
        EstadoPedido estadoAnterior,
        EstadoPedido estadoResultante,
        Integer numeroIntento,
        Long reportadoPorUsuarioId,
        OffsetDateTime fecha
) {
    public static IncidenciaResponseDTO de(IncidenciaPedido i) {
        return new IncidenciaResponseDTO(i.getId(), i.getPedidoId(), i.getTipo(), i.getTipo().descripcion(),
                i.getComentario(), i.getLatitud(), i.getLongitud(), i.getEstadoAnterior(), i.getEstadoResultante(),
                i.getNumeroIntento(), i.getUsuarioId(), i.getFecha().atOffset(ZoneOffset.UTC));
    }
}

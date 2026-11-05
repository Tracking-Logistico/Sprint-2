package com.udea.demo.pedidos.application.dto;

import com.udea.demo.pedidos.domain.model.EstadoPedido;
import com.udea.demo.pedidos.domain.model.EtapaUbicacion;
import com.udea.demo.pedidos.domain.model.OrigenUbicacion;

import java.time.LocalDateTime;

public record UbicacionResponseDTO(
        Long id,
        Long pedidoId,
        String numeroTracking,
        EtapaUbicacion etapa,
        EstadoPedido estadoPedido,
        Long ciudadId,
        String ciudad,
        Long oficinaId,
        String oficina,
        Long puntoEntregaId,
        String puntoEntrega,
        String direccion,
        String vehiculoId,
        Long usuarioId,
        OrigenUbicacion origen,
        LocalDateTime fechaEvento,
        LocalDateTime fechaRegistro,
        Long corrigeEventoId
) {}

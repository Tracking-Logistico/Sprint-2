package com.udea.demo.pedidos.application.dto;

import com.udea.demo.pedidos.domain.model.EstadoPedido;

public record GestionEnvioResponseDTO(
        Long pedidoId,
        String numeroTracking,
        EstadoPedido estado,
        String numeroTrackingDevolucion
) {}

package com.udea.demo.pedidos.application.dto;

import com.udea.demo.pedidos.domain.model.EstadoPedido;
import jakarta.validation.constraints.NotNull;

/** Estado logístico (operador). HU-13 13-BE-03: whitelist estricta. */
@SoloCamposDeclarados
public record CambiarEstadoLogisticoRequestDTO(@NotNull EstadoPedido estado) {}

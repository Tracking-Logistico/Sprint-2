package com.udea.demo.pedidos.application.dto;

import com.udea.demo.pedidos.domain.model.Prioridad;
import jakarta.validation.constraints.Size;

/** Validación del operador. HU-13 13-BE-03: whitelist estricta; el flag 18+ responde 403. */
@SoloCamposDeclarados
public record ValidarPedidoRequestDTO(
    Boolean aprobar,
    Prioridad prioridadConfirmada,
    @Size(max = 500) String observaciones,
    @Size(max = 500) String justificacionPrioridad,
    @Size(max = 120) String campoObservado,
    Boolean solicitarCorreccion
) {}

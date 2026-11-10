package com.udea.demo.rutas.application.dto;

import com.udea.demo.pedidos.domain.model.OrigenCheckpoint;
import com.udea.demo.rutas.domain.model.ResultadoVerificacionEdad;
import jakarta.validation.constraints.NotNull;

/**
 * HU-13 CA-5/6/7: botón del conductor. "Mayor de edad verificado" = CUMPLE; "Verificación no aprobada" = NO_CUMPLE.
 * Solo se recibe el resultado: no hay campos para el número de documento, la fecha de nacimiento ni imágenes.
 *
 * @param origen EN_LINEA (por defecto) u OFFLINE, para la auditoría. Lista blanca por enum.
 */
public record VerificacionEdadRequestDTO(
        @NotNull(message = "El resultado de la verificación es obligatorio") ResultadoVerificacionEdad resultado,
        OrigenCheckpoint origen) {
}

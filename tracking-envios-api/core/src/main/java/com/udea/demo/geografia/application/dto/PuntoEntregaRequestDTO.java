package com.udea.demo.geografia.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PuntoEntregaRequestDTO(
        @NotBlank @Size(max = 120) String nombre,
        @NotNull Long ciudadId,
        Long oficinaId,
        @NotBlank @Size(max = 250) String direccion,
        @Size(max = 500) String descripcion
) {}

package com.udea.demo.geografia.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CiudadRequestDTO(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 100) String departamento
) {}

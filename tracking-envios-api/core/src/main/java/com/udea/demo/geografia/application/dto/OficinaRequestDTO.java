package com.udea.demo.geografia.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OficinaRequestDTO(
        @NotBlank @Size(max = 120) String nombre,
        @NotNull Long ciudadId,
        @NotBlank @Size(max = 250) String direccion,
        @NotBlank @Pattern(regexp = "^[+0-9() .-]{7,20}$") String telefono,
        @Email @Size(max = 255) String email
) {}

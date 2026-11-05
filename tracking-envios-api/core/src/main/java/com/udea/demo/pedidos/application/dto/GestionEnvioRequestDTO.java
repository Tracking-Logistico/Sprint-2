package com.udea.demo.pedidos.application.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GestionEnvioRequestDTO(
        @NotBlank @Size(max = 500) String motivo,
        @NotNull @AssertTrue(message = "Debes confirmar la operación") Boolean confirmar
) {}

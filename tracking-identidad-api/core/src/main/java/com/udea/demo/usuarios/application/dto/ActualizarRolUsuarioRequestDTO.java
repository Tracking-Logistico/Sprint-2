package com.udea.demo.usuarios.application.dto;

import com.udea.demo.usuarios.domain.model.Rol;
import jakarta.validation.constraints.NotNull;

/** Cambio de rol administrativo. HU-13 CA-3: no admite cédula ni fecha de nacimiento. */
@SinCamposIdentidad
public record ActualizarRolUsuarioRequestDTO(
        @NotNull(message = "El rol es obligatorio") Rol rol,
        String licencia,
        String codigoEmpleado
) {}

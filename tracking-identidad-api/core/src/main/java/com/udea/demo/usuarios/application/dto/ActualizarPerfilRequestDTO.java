package com.udea.demo.usuarios.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Edición del perfil (HU-01A/B). HU-13 CA-3: no admite cédula ni fecha de nacimiento. */
@SinCamposIdentidad
public record ActualizarPerfilRequestDTO(
    @NotBlank(message = "El nombre es obligatorio") @Size(max = 100) String nombre,
    @Pattern(regexp = "^$|^\\+?[1-9][0-9]{7,14}$", message = "El teléfono debe usar un formato internacional válido") String telefono,
    @Size(max = 200, message = "La dirección no puede superar 200 caracteres") String direccion
) {}

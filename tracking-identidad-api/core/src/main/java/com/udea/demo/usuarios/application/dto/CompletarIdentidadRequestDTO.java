package com.udea.demo.usuarios.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * HU-13: un usuario con el perfil incompleto (V26) completa su cédula y su fecha de nacimiento, una sola vez.
 * La forma se valida aquí; la semántica (formato de cédula, fecha real y no futura) en ProteccionDatosIdentidadService.
 */
public record CompletarIdentidadRequestDTO(
        @NotBlank(message = "El número de cédula es obligatorio")
        @Size(max = 20, message = "El número de cédula no tiene un formato válido")
        String numeroCedula,
        @NotBlank(message = "La fecha de nacimiento es obligatoria")
        @Pattern(regexp = "^$|^[0-9]{4}-[0-9]{2}-[0-9]{2}$", message = "La fecha de nacimiento debe tener el formato AAAA-MM-DD")
        String fechaNacimiento) {

    @Override
    public String toString() {
        return "CompletarIdentidadRequestDTO[numeroCedula=***, fechaNacimiento=***]";
    }
}

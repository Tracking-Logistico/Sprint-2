package com.udea.demo.usuarios.application.dto;

import java.time.LocalDate;

/**
 * HU-13 CA-2 y CA-3: identidad del propio usuario, en solo lectura.
 *
 * @param registrada              si ya registró la cédula y la fecha de nacimiento
 * @param numeroCedulaEnmascarado solo los últimos 4 dígitos (****4321); nunca la cédula completa
 * @param fechaNacimiento         fecha completa, visible solo para su titular
 * @param edad                    calculada en el servidor a la fecha actual; nunca se acepta desde el cliente
 * @param editable                false una vez registrada: los datos son inmutables desde el perfil
 */
public record IdentidadPerfilResponseDTO(boolean registrada, String numeroCedulaEnmascarado, LocalDate fechaNacimiento,
                                         Integer edad, boolean editable) {
    public static final IdentidadPerfilResponseDTO PENDIENTE = new IdentidadPerfilResponseDTO(false, null, null, null, true);

    @Override
    public String toString() {
        return "IdentidadPerfilResponseDTO[registrada=" + registrada + ", editable=" + editable + "]";
    }
}

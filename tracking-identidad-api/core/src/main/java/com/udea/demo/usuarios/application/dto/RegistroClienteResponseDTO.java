package com.udea.demo.usuarios.application.dto;

/**
 * Respuesta del registro público (HU-13 13-BE-01, anti-enumeración). Es idéntica tanto si se creó la cuenta
 * como si la cédula ya pertenecía a otra cuenta, así que quien se registra no puede saber si una cédula existe.
 * No incluye ningún dato de la cuenta.
 */
public record RegistroClienteResponseDTO(String mensaje) {
    public static final RegistroClienteResponseDTO NEUTRA =
            new RegistroClienteResponseDTO("Si los datos son válidos, recibirás un correo para verificar tu cuenta");
}

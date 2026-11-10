package com.udea.demo.usuarios.domain.model;

/**
 * Datos de identidad cifrados en reposo (HU-13). Cada campo tiene su propio dato asociado (AAD) en AES-GCM:
 * un texto cifrado de la cédula no se puede copiar a la columna de la fecha, ni al revés, sin que el
 * descifrado falle.
 */
public enum CampoIdentidad {
    NUMERO_CEDULA("usuarios.numero_cedula_cifrado"),
    FECHA_NACIMIENTO("usuarios.fecha_nacimiento_cifrada");

    private final String contexto;

    CampoIdentidad(String contexto) {
        this.contexto = contexto;
    }

    public String contexto() {
        return contexto;
    }
}

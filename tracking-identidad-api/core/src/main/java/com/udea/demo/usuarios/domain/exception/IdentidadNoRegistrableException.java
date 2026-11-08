package com.udea.demo.usuarios.domain.exception;

/**
 * HU-13 (13-BE-01): la cédula no se puede asociar a este usuario. El mensaje es deliberadamente genérico:
 * no dice si la cédula ya pertenece a otra cuenta (anti-enumeración), ni repite la cédula ni su hash.
 */
public class IdentidadNoRegistrableException extends RuntimeException {
    public static final String MENSAJE = "No fue posible crear el usuario con los datos de identidad suministrados";

    public IdentidadNoRegistrableException() {
        super(MENSAJE);
    }
}

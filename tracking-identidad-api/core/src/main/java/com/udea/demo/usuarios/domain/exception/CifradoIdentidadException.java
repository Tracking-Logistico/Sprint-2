package com.udea.demo.usuarios.domain.exception;

/**
 * Error al cifrar o descifrar un dato de identidad: clave ausente, texto cifrado manipulado, versión de
 * clave desconocida, etc. El mensaje es genérico y nunca contiene el dato, el texto cifrado ni la clave.
 */
public class CifradoIdentidadException extends RuntimeException {
    public CifradoIdentidadException(String mensaje) {
        super(mensaje);
    }

    public CifradoIdentidadException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}

package com.udea.demo.usuarios.domain.exception;

/**
 * Un dato de identidad (cédula o fecha de nacimiento) no supera la validación (CA-1). El mensaje es fijo y
 * va dirigido al campo. Nunca incluye el valor recibido, para no reflejar datos personales ni entradas
 * maliciosas en respuestas o logs.
 */
public class DatoIdentidadInvalidoException extends RuntimeException {
    private final String campo;

    public DatoIdentidadInvalidoException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}

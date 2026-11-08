package com.udea.demo.usuarios.domain.exception;

/** CA-3: la cédula y la fecha de nacimiento ya están registradas y no se pueden modificar. */
public class DatosIdentidadInmutablesException extends RuntimeException {
    public DatosIdentidadInmutablesException() {
        super("La cédula y la fecha de nacimiento ya fueron registradas y no se pueden modificar");
    }
}

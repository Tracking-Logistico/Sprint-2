package com.udea.demo.pedidos.domain.exception;

/**
 * HU-13 CA-5: un envío con "Verificación de edad obligatoria (18+)" no puede pasar a ENTREGADO sin un resultado
 * "Mayor de edad verificado" (CUMPLE). Error controlado (409) con mensaje fijo, sin datos del envío.
 */
public class VerificacionEdadRequeridaException extends IllegalStateException {
    public static final String MENSAJE = "El envío requiere verificación de edad aprobada antes de marcarse como entregado";

    public VerificacionEdadRequeridaException() {
        super(MENSAJE);
    }
}

package com.udea.demo.rutas.domain.model;

/** HU-13: resultado de la verificación de edad 18+. Es el único dato que se registra; nunca el documento. */
public enum ResultadoVerificacionEdad {
    /** "Mayor de edad verificado" (CA-6): permite cerrar la entrega como ENTREGADO. */
    CUMPLE,
    /** "Verificación no aprobada" (CA-7): impide ENTREGADO e inicia el retorno a bodega. */
    NO_CUMPLE;

    public boolean permiteEntrega() {
        return this == CUMPLE;
    }
}

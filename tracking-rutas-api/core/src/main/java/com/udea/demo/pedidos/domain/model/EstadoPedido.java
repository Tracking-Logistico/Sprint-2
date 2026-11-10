package com.udea.demo.pedidos.domain.model;

import java.util.EnumSet;
import java.util.Set;

public enum EstadoPedido {
    SOLICITADO,
    CORRECCION_SOLICITADA,
    CREADO,
    RECIBIDO_EN_ORIGEN,
    EN_TRANSITO,
    EN_REPARTO,
    ENTREGADO,
    RECHAZADO,
    ENTREGA_FALLIDA,
    ENTREGA_REPROGRAMADA,
    DIRECCION_POR_VERIFICAR,
    DEVOLUCION_AL_REMITENTE,
    ENTREGA_FALLIDA_CERRADA,
    PAUSADO,
    CANCELADO,
    /** HU-13 CA-7: "Entrega fallida (Verificación de edad no aprobada)". Final: el paquete vuelve a bodega. */
    ENTREGA_FALLIDA_VERIFICACION_EDAD,

    RECIBIDO,
    EN_VALIDACION,
    VALIDADO;

    private static final Set<EstadoPedido> FINALES =
            EnumSet.of(ENTREGADO, ENTREGA_FALLIDA_VERIFICACION_EDAD, RECHAZADO, DEVOLUCION_AL_REMITENTE, ENTREGA_FALLIDA_CERRADA);

    private static final Set<EstadoPedido> ACTIVOS_LOGISTICOS = EnumSet.of(CREADO, RECIBIDO_EN_ORIGEN, EN_TRANSITO,
            EN_REPARTO, ENTREGA_FALLIDA, ENTREGA_REPROGRAMADA, DIRECCION_POR_VERIFICAR);

    public boolean puedeValidarse() {
        return this == SOLICITADO || this == CORRECCION_SOLICITADA || this == RECIBIDO || this == EN_VALIDACION;
    }

    public boolean puedeActivarTracking() {
        return this == SOLICITADO || this == VALIDADO;
    }

    /** Estados que cierran el flujo logístico: ningún evento posterior puede modificar el estado. */
    public boolean esFinal() {
        return FINALES.contains(this);
    }

    /** Envío con tracking activo que aún se encuentra en la operación logística. */
    public boolean esActivoLogistico() {
        return ACTIVOS_LOGISTICOS.contains(this);
    }
}

package com.udea.demo.pedidos.domain.anomalias;

public enum TipoAnomalia {
    PERMANENCIA_EXCESIVA("Tu paquete lleva más tiempo del previsto en una oficina. Estamos revisando su avance."),
    INACTIVIDAD_REPARTO("La ruta de tu paquete presenta una demora. Puedes contactar a la oficina responsable."),
    REINTENTOS_ACUMULADOS("No hemos podido completar dos intentos de entrega. Contacta a la oficina para coordinar los siguientes pasos."),
    SIN_CONFIRMACION_RECEPCION("Tu paquete llegó a destino y estamos pendientes de confirmar su recepción en oficina.");

    private final String mensaje;

    TipoAnomalia(String mensaje) { this.mensaje = mensaje; }

    public String mensajeCliente() { return mensaje; }
}

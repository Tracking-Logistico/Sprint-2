package com.udea.demo.pedidos.domain.model;

public enum MotivoDevolucion {
    PAQUETE_RECHAZADO("El destinatario rechazó el paquete"),
    MAXIMO_INTENTOS("Se alcanzó el número máximo de intentos de entrega"),
    PLAZO_DIRECCION_VENCIDO("No se confirmó la dirección dentro del plazo establecido"),
    PLAZO_REPROGRAMACION_VENCIDO("No se reprogramó la entrega dentro del plazo establecido"),
    CANCELACION("El envío fue cancelado por un usuario autorizado");

    private final String descripcion;

    MotivoDevolucion(String descripcion) { this.descripcion = descripcion; }

    public String descripcion() { return descripcion; }
}

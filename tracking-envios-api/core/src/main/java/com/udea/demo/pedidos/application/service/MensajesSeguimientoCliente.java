package com.udea.demo.pedidos.application.service;

import com.udea.demo.pedidos.domain.model.EstadoPedido;

/** Traduce estados internos a lenguaje orientado al cliente (sin códigos de sistema). */
public final class MensajesSeguimientoCliente {
    public static final String PUNTO_CONTROL = "Tu paquete pasó por un punto de control de la ruta";

    private MensajesSeguimientoCliente() {}

    public static String estado(EstadoPedido estado) {
        return switch (estado) {
            case CREADO -> "Tu envío fue registrado y está listo para ser recogido";
            case RECIBIDO_EN_ORIGEN -> "Recibimos tu paquete en nuestro centro de origen";
            case EN_TRANSITO -> "Tu paquete está en camino hacia la ciudad de destino";
            case EN_REPARTO -> "Tu paquete salió a reparto";
            case ENTREGADO -> "Tu paquete fue entregado";
            case ENTREGA_FALLIDA -> "No pudimos entregar tu paquete; puedes elegir una nueva fecha de entrega";
            case ENTREGA_REPROGRAMADA -> "La entrega de tu paquete fue reprogramada";
            case DIRECCION_POR_VERIFICAR -> "Necesitamos que confirmes la dirección de entrega";
            case DEVOLUCION_AL_REMITENTE -> "Tu paquete está siendo devuelto al remitente";
            case ENTREGA_FALLIDA_CERRADA -> "La entrega no pudo completarse y el caso fue cerrado";
            case PAUSADO -> "Tu envío está pausado";
            case CANCELADO -> "Tu envío fue cancelado";
            case RECHAZADO -> "Tu solicitud de envío no fue aprobada";
            default -> "Tu envío está en proceso";
        };
    }
}

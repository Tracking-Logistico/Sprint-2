package com.udea.demo.usuarios.application.service;

import com.udea.demo.pedidos.domain.model.EstadoPedido;
import com.udea.demo.pedidos.domain.model.EtapaCheckpoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Construye asunto y cuerpo del mensaje por hito, con enlace directo al portal.
 * No incluye dirección completa ni datos sensibles (cumplimiento de protección de datos).
 */
@Component
public class PlantillaMensaje {

    private final String trackingUrl;

    public PlantillaMensaje(@Value("${app.notifications.tracking-url:http://localhost:5173/tracking}") String trackingUrl) {
        this.trackingUrl = trackingUrl;
    }

    public record Mensaje(String asunto, String cuerpo) {}

    /** Mensaje para hitos derivados de CheckpointRegistradoEvent (EN_TRANSITO, EN_REPARTO, ENTREGADO). */
    public Mensaje porEstado(String numeroTracking, EstadoPedido estado) {
        String enlace = enlace(numeroTracking);
        String cuerpoBase = switch (estado) {
            case EN_TRANSITO -> "Tu paquete está en camino hacia la ciudad de destino.";
            case EN_REPARTO  -> "Tu paquete salió a reparto y llegará hoy.";
            case ENTREGADO   -> "Tu paquete fue entregado. ¡Gracias por confiar en nosotros!";
            case ENTREGA_FALLIDA -> "No pudimos entregar tu paquete. Puedes elegir una nueva fecha de entrega.";
            case ENTREGA_REPROGRAMADA -> "Tu entrega fue reprogramada.";
            case ENTREGA_FALLIDA_VERIFICACION_EDAD -> "No pudimos entregar tu paquete porque no fue posible verificar "
                    + "la mayoría de edad de quien lo recibe. El paquete regresará a nuestra bodega.";
            // Nunca se expone el código interno del estado al cliente (HU-13 13-FE-04, regla 5).
            default -> "Tu envío tiene una nueva actualización.";
        };
        String asunto = "Actualización de tu envío " + numeroTracking;
        String cuerpo = "Hola,\n\n" + cuerpoBase
                + "\n\nPuedes ver el detalle en: " + enlace
                + "\n\n— Tracking Logístico";
        return new Mensaje(asunto, cuerpo);
    }

    /** Para eventos que ya traen mensajeCliente redactado. */
    public Mensaje porMensajeCliente(String numeroTracking, String mensajeCliente) {
        String asunto = "Actualización de tu envío " + numeroTracking;
        String cuerpo = "Hola,\n\n" + mensajeCliente
                + "\n\nPuedes ver el detalle en: " + enlace(numeroTracking)
                + "\n\n— Tracking Logístico";
        return new Mensaje(asunto, cuerpo);
    }

    /** SMS: solo código, estado y enlace. Sin dirección ni datos personales. */
    public String cuerpoSms(String numeroTracking, String resumen) {
        return "Tracking Logístico: " + resumen + " Envío " + numeroTracking + ". Ver: " + enlace(numeroTracking);
    }

    private String enlace(String numeroTracking) {
        String base = trackingUrl.endsWith("/") ? trackingUrl.substring(0, trackingUrl.length() - 1) : trackingUrl;
        return base + "?code=" + numeroTracking;
    }
}
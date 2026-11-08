package com.udea.demo.pedidos.application.service;

import com.udea.demo.pedidos.domain.event.DevolucionIniciadaEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DevolucionIniciadaListener {
    private final CrearDevolucionService devoluciones;

    public DevolucionIniciadaListener(CrearDevolucionService devoluciones) {
        this.devoluciones = devoluciones;
    }

    @EventListener
    public void crearEnvioDeDevolucion(DevolucionIniciadaEvent evento) {
        devoluciones.crear(evento);
    }
}

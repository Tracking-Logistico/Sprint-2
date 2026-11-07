package com.udea.demo.pedidos.infrastructure.scheduling;

import com.udea.demo.pedidos.application.service.DevolucionReprogramacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class EscalamientoReprogramacionesVencidasTask {
    private static final Logger log = LoggerFactory.getLogger(EscalamientoReprogramacionesVencidasTask.class);
    private final DevolucionReprogramacionService devoluciones;

    public EscalamientoReprogramacionesVencidasTask(DevolucionReprogramacionService devoluciones) {
        this.devoluciones = devoluciones;
    }

    @Scheduled(cron = "${app.incidencias.escalamiento-cron:0 0 * * * *}")
    public void escalar() {
        LocalDateTime ahora = LocalDateTime.now();
        for (Long pedidoId : devoluciones.pendientesDeEscalar(ahora)) {
            try {
                if (devoluciones.escalarVencida(pedidoId, ahora)) {
                    log.info("Envío {} escalado a devolución por vencimiento del plazo de reprogramación", pedidoId);
                }
            } catch (RuntimeException ex) {
                log.error("No fue posible escalar a devolución el envío {}", pedidoId, ex);
            }
        }
    }
}

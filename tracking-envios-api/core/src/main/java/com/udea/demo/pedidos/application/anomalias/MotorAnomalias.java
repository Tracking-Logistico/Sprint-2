package com.udea.demo.pedidos.application.anomalias;

import com.udea.demo.pedidos.domain.anomalias.TipoAnomalia;
import com.udea.demo.pedidos.interfaces.persistence.PedidoRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Clock;

@Service
public class MotorAnomalias {
    private static final Logger log = LoggerFactory.getLogger(MotorAnomalias.class);
    private final RepositorioAnomalias anomalias;
    private final PedidoRepository pedidos;
    private final ReglasAnomalias reglas;
    private final Clock reloj;
    private final TransactionTemplate transaccion;

    public MotorAnomalias(RepositorioAnomalias anomalias, PedidoRepository pedidos, ReglasAnomalias reglas,
            @Qualifier("relojAnomalias") Clock reloj, PlatformTransactionManager transacciones) {
        this.anomalias=anomalias;
        this.pedidos=pedidos;
        this.reglas=reglas;
        this.reloj=reloj;
        this.transaccion=new TransactionTemplate(transacciones);
        this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transaccion.setTimeout(30);
    }

    public void evaluar(long envio) {
        for (TipoAnomalia tipo : TipoAnomalia.values()) {
            try {
                transaccion.executeWithoutResult(status -> pedidos.findByIdForUpdate(envio).ifPresent(pedido -> {
                    var ahora=reloj.instant();
                    var condicion=reglas.evaluar(pedido,tipo,anomalias.parametros(),ahora);
                    anomalias.reconciliar(envio,tipo,condicion,ahora);
                }));
            } catch (RuntimeException ex) {

                log.warn("Evaluación de anomalías fallida regla={} causa={}",tipo,ex.getClass().getSimpleName());
            }
        }
    }
}

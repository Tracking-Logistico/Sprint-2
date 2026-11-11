package com.udea.demo.pedidos.application.anomalias;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Component
@ConditionalOnProperty(name="app.anomalias.enabled",havingValue="true")
public class MonitoreoAnomaliasTask {
    private static final Logger log=LoggerFactory.getLogger(MonitoreoAnomaliasTask.class);
    private final DataSource dataSource;
    private final RepositorioAnomalias anomalias;
    private final MotorAnomalias motor;
    private final int lote;

    public MonitoreoAnomaliasTask(DataSource dataSource,RepositorioAnomalias anomalias,MotorAnomalias motor,
            @Value("${app.anomalias.lote:100}") int lote) {
        if (lote<1 || lote>500) throw new IllegalArgumentException("Tamaño de lote inválido");
        this.dataSource=dataSource;
        this.anomalias=anomalias;
        this.motor=motor;
        this.lote=lote;
    }

    @Scheduled(fixedDelayString="${app.anomalias.intervalo-ms:60000}",initialDelayString="${app.anomalias.inicio-ms:60000}")
    public void ejecutar() {


        try (Connection conexion=dataSource.getConnection()) {
            conexion.setAutoCommit(false);
            try {
                try (var sentencia=conexion.prepareStatement("SELECT id FROM bloqueo_motor_anomalias WHERE id=1 FOR UPDATE NOWAIT")) {
                    try (var fila=sentencia.executeQuery()) {
                        if (!fila.next()) throw new SQLException("No existe el bloqueo del motor");
                    }
                }
                long hasta=anomalias.ultimoPedido();
                long despues=0;
                while (true) {
                    var ids=anomalias.siguientes(despues,hasta,lote);
                    if (ids.isEmpty()) break;
                    for (Long id : ids) motor.evaluar(id);
                    despues=ids.get(ids.size()-1);
                }
            } finally {
                conexion.rollback();
            }
        } catch (RuntimeException ex) {
            log.warn("Monitoreo no disponible causa={}",ex.getClass().getSimpleName());
        } catch (SQLException ex) {
            if (!"55P03".equals(ex.getSQLState()) && !"HYT00".equals(ex.getSQLState())) {
                log.warn("Monitoreo no disponible causa={}",ex.getClass().getSimpleName());
            }
        }
    }
}

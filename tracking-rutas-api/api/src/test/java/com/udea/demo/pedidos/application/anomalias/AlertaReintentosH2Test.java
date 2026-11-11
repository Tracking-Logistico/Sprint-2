package com.udea.demo.pedidos.application.anomalias;

import com.udea.demo.pedidos.domain.event.IncidenciaRegistradaEvent;
import com.udea.demo.pedidos.domain.model.EstadoPedido;
import com.udea.demo.pedidos.domain.model.TipoIncidencia;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class AlertaReintentosH2Test {
    @Test
    void elPerfilH2DeRutasSoportaLaEscuchaInmediataSinDuplicados() throws Exception {
        try (var conexion=DriverManager.getConnection("jdbc:h2:mem:rutas_hu18_"+UUID.randomUUID()+";MODE=PostgreSQL")) {
            try (var s=conexion.createStatement()) {
                s.execute("CREATE TABLE usuarios(id BIGINT PRIMARY KEY)");
                s.execute("CREATE TABLE pedidos(id_pedido BIGINT PRIMARY KEY)");
                s.execute("INSERT INTO usuarios VALUES (1)");
                s.execute("INSERT INTO pedidos VALUES (1)");
            }
            ScriptUtils.executeSqlScript(conexion,new ClassPathResource("db/h2/schema.sql"));
            var jdbc=new JdbcTemplate(new SingleConnectionDataSource(conexion,true));
            var reloj=Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"),ZoneOffset.UTC);
            var escucha=new AlertaReintentosListener(jdbc,reloj);
            var evento=new IncidenciaRegistradaEvent(UUID.randomUUID(),IncidenciaRegistradaEvent.TIPO,1,
                    LocalDateTime.ofInstant(reloj.instant(),ZoneOffset.UTC),1L,"T-SINTETICO",1L,2L,1L,
                    TipoIncidencia.CLIENTE_AUSENTE,null,null,EstadoPedido.EN_REPARTO,EstadoPedido.ENTREGA_FALLIDA,2,null);
            conexion.setAutoCommit(false);
            escucha.alRegistrar(evento);
            escucha.alRegistrar(evento);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM anomalias",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT evento_origen FROM anomalias",String.class)).isEqualTo("INTENTO-2");
            jdbc.update("UPDATE anomalias SET estado='DESCARTADA',clave_activa=NULL,cerrada_en=CURRENT_TIMESTAMP,cerrada_por=1,origen_cierre='WEB'");
            escucha.alRegistrar(evento);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM anomalias",Long.class)).isEqualTo(1);
            conexion.rollback();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM anomalias",Long.class)).isZero();
        }
    }
}

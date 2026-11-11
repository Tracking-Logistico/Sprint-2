package com.udea.demo.pedidos.application.anomalias;

import com.udea.demo.pedidos.domain.anomalias.CondicionAnomalia;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class AnomaliasReglasTest {
    @Test
    void losTresUmbralesSonEstrictos() {
        Instant inicio=Instant.parse("2030-01-01T00:00:00Z");
        for (int horas : new int[]{24,4,2}) {
            var condicion=new CondicionAnomalia("OPERACION-1",1L,inicio,horas,null);
            assertThat(condicion.excedidaEn(inicio.plusSeconds(horas*3600L-1))).isFalse();
            assertThat(condicion.excedidaEn(inicio.plusSeconds(horas*3600L))).isFalse();
            assertThat(condicion.excedidaEn(inicio.plusSeconds(horas*3600L+1))).isTrue();
        }
    }

    @Test
    void telefonoNoAdmiteEsquemasNiInyeccion() {
        assertThat(GestionAnomaliasService.telefonoSeguro("javascript:alert(1)")).isNull();
        assertThat(GestionAnomaliasService.telefonoSeguro("+9990000000\r\nX:evil")).isNull();
        assertThat(GestionAnomaliasService.telefonoSeguro("<img src=x>")).isNull();
        assertThat(GestionAnomaliasService.telefonoSeguro("+999 (000) 0000")).isEqualTo("+9990000000");
    }
}

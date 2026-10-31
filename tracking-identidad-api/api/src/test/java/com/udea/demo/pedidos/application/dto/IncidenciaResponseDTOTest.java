package com.udea.demo.pedidos.application.dto;

import com.udea.demo.pedidos.domain.model.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IncidenciaResponseDTOTest {
    @Test
    void identificaUtcYConservaElInstanteParaMostrarHoraColombiana() {
        LocalDateTime fechaUtc = LocalDateTime.of(2026, 10, 31, 21, 25);
        IncidenciaPedido incidencia = new IncidenciaPedido(1L, 2L,
                TipoIncidencia.RETRASO_OPERATIVO, null,
                new UbicacionReportada(null, null, null, false),
                EstadoPedido.EN_TRANSITO, EstadoPedido.EN_TRANSITO, null, fechaUtc);

        var fecha = IncidenciaResponseDTO.de(incidencia).fecha();

        assertThat(fecha.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(fecha.toLocalDateTime()).isEqualTo(fechaUtc);
        assertThat(fecha.atZoneSameInstant(ZoneId.of("America/Bogota")).getHour()).isEqualTo(16);
        assertThat(fecha.getMinute()).isEqualTo(25);
        assertThat(incidencia.getFecha()).isEqualTo(fechaUtc);
    }
}

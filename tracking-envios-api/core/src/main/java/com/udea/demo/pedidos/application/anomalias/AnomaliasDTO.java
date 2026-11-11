package com.udea.demo.pedidos.application.anomalias;

import com.udea.demo.pedidos.domain.anomalias.*;
import com.udea.demo.pedidos.application.dto.SoloCamposDeclarados;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class AnomaliasDTO {
    private AnomaliasDTO() {}

    public record Parametros(int permanenciaHoras, int inactividadHoras, int recepcionHoras, long version) {}

    @SoloCamposDeclarados
    public record CambiarParametros(@Min(1) @Max(168) int permanenciaHoras,
            @Min(1) @Max(168) int inactividadHoras, @Min(1) @Max(168) int recepcionHoras,
            @NotNull @PositiveOrZero Long version, @NotNull OrigenAnomalia origen) {}

    @SoloCamposDeclarados
    public record Cerrar(@NotNull EstadoAnomalia estado, @NotNull OrigenAnomalia origen) {}

    @SoloCamposDeclarados
    public record Recepcion(@NotNull @Positive Long eventoLlegadaId, @NotNull OrigenAnomalia origen) {}

    public record AlertaCliente(String mensaje, String oficina, String telefono, String enlaceLlamada) {}

    public record Llegada(long envioId, long eventoLlegadaId, String numeroTracking, String oficina,
                          java.time.LocalDateTime llegada) {}

    public record Anomalia(long id, long envioId, String numeroTracking, TipoAnomalia tipo,
            EstadoAnomalia estado, String eventoOrigen, Long oficinaId, String oficina,
            Instant inicioUtc, Integer umbralHoras, Integer intentos, Instant creadaEn,
            Instant cerradaEn, Long cerradaPor, String origenCierre) {}

    public record Pagina(List<Anomalia> contenido, int pagina, int tamano, long total) {}
}

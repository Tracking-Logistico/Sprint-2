package com.udea.demo.pedidos.application.dto;

import com.udea.demo.pedidos.domain.model.EtapaUbicacion;
import com.udea.demo.pedidos.domain.model.OrigenUbicacion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record RegistrarUbicacionRequestDTO(
        @NotNull EtapaUbicacion etapa,
        @NotNull OrigenUbicacion origen,
        @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        String idEvento,
        @Size(max = 120) String codigoQr,
        @NotNull Long ciudadId,
        Long oficinaId,
        Long puntoEntregaId,
        @Size(max = 80) String vehiculoId,
        @NotNull OffsetDateTime fechaEvento,
        Long corrigeEventoId
) {}

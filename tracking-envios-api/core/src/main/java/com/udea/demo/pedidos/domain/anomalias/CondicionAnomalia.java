package com.udea.demo.pedidos.domain.anomalias;

import java.time.Instant;


public record CondicionAnomalia(String eventoOrigen, Long oficinaId, Instant inicio,
                                Integer umbralHoras, Integer intentos) {
    public boolean excedidaEn(Instant ahora) {
        return umbralHoras == null || ahora.isAfter(inicio.plusSeconds(umbralHoras * 3600L));
    }
}

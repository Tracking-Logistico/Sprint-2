package com.udea.demo.pedidos.interfaces.persistence;

import com.udea.demo.pedidos.domain.model.EventoUbicacionPedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventoUbicacionPedidoRepository extends JpaRepository<EventoUbicacionPedido, Long> {
    Optional<EventoUbicacionPedido> findByIdEvento(String idEvento);
    List<EventoUbicacionPedido> findByPedidoIdOrderByFechaEventoAscIdAsc(Long pedidoId);
}

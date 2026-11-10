package com.udea.demo.rutas.interfaces.persistence;

import com.udea.demo.rutas.domain.model.EstadoParada;
import com.udea.demo.rutas.domain.model.ParadaRuta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParadaRutaRepository extends JpaRepository<ParadaRuta, Long> {

    Optional<ParadaRuta> findByPedidoIdAndEstado(Long pedidoId, EstadoParada estado);

    @org.springframework.data.jpa.repository.Query("select c.usuario.id from ParadaRuta p, Conductor c " +
           "where p.ruta.conductorId = c.id and p.pedidoId = :pedidoId and p.estado = :estado")
    Optional<Long> findConductorUsuarioIdByPedidoIdAndEstado(
        @org.springframework.data.repository.query.Param("pedidoId") Long pedidoId,
        @org.springframework.data.repository.query.Param("estado") EstadoParada estado);

    @Query("select case when count(p) > 0 then true else false end from ParadaRuta p, Conductor c " +
           "where p.ruta.conductorId = c.id and c.usuario.id = :usuarioId " +
           "and p.pedidoId = :pedidoId and p.estado = :estado")
    boolean existsByPedidoIdAndRutaConductorUsuarioIdAndEstado(
        @org.springframework.data.repository.query.Param("pedidoId") Long pedidoId,
        @org.springframework.data.repository.query.Param("usuarioId") Long usuarioId,
        @org.springframework.data.repository.query.Param("estado") EstadoParada estado);

    @Query("select case when count(p) > 0 then true else false end from ParadaRuta p, Conductor c " +
           "where p.ruta.conductorId = c.id and c.usuario.id = :usuarioId and p.pedidoId = :pedidoId")
    boolean existsByPedidoIdAndRutaConductorUsuarioId(
        @org.springframework.data.repository.query.Param("pedidoId") Long pedidoId,
        @org.springframework.data.repository.query.Param("usuarioId") Long usuarioId);

    @Query("select p.pedidoId from ParadaRuta p where p.estado = :estado")
    List<Long> findPedidoIdsByEstado(EstadoParada estado);
}

package com.udea.demo.pedidos.interfaces.persistence;

import com.udea.demo.pedidos.domain.model.EstadoPedido;
import com.udea.demo.pedidos.domain.model.Pedido;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    List<Pedido> findByIdIn(java.util.Collection<Long> ids);
    List<Pedido> findByEstadoInOrderByFechaCreacionAsc(List<EstadoPedido> estados);

    Page<Pedido> findByEstadoInOrderByFechaCreacionAsc(List<EstadoPedido> estados, Pageable pageable);

    @Query("""
        select p from Pedido p
        where p.estado = :correccion
           or (p.estado = :solicitado and p.fechaValidacion is null)
        order by p.fechaCreacion asc
        """)
    Page<Pedido> findPendientes(@Param("solicitado") EstadoPedido solicitado,
                                @Param("correccion") EstadoPedido correccion,
                                Pageable pageable);

    Page<Pedido> findByEstadoAndFechaValidacionIsNotNullOrderByFechaCreacionAsc(
            EstadoPedido estado, Pageable pageable);

    @Query("""
        select p from Pedido p
        where p.estado = :creado
           or (p.estado = :solicitado and p.fechaValidacion is not null)
        order by p.fechaCreacion asc
        """)
    Page<Pedido> findActivables(@Param("solicitado") EstadoPedido solicitado,
                                @Param("creado") EstadoPedido creado,
                                Pageable pageable);
    @Query("""
        select p from Pedido p
        where (p.clienteId = :clienteId
            or lower(p.remitenteEmail) = lower(:email)
            or (lower(p.destinatarioEmail) = lower(:email)
                and p.estado not in :estadosNoAsociados))
            and (cast(:estado as string) is null or p.estado = :estado)
            and (cast(:fechaDesde as timestamp) is null or p.fechaCreacion >= :fechaDesde)
            and (cast(:fechaHasta as timestamp) is null or p.fechaCreacion < :fechaHasta)
        """)
    Page<Pedido> findPedidosDeCliente(@Param("clienteId") Long clienteId,
                                     @Param("email") String email,
                                     @Param("estadosNoAsociados") List<EstadoPedido> estadosNoAsociados,
                                     @Param("estado") EstadoPedido estado,
                                     @Param("fechaDesde") LocalDateTime fechaDesde,
                                     @Param("fechaHasta") LocalDateTime fechaHasta,
                                     Pageable pageable);
    Optional<Pedido> findByNumeroTracking(String numeroTracking);
    Optional<Pedido> findByPedidoOrigenDevolucionId(Long pedidoOrigenDevolucionId);
    boolean existsByNumeroTracking(String numeroTracking);
    boolean existsByNumeroPedido(String numeroPedido);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.id = :id")
    Optional<Pedido> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pedido p where p.numeroTracking = :numeroTracking")
    Optional<Pedido> findByNumeroTrackingForUpdate(@Param("numeroTracking") String numeroTracking);

    @Query("""
        select p.id from Pedido p
        where p.estado = :estado and p.fechaLimiteVerificacionDireccion < :ahora
        order by p.fechaLimiteVerificacionDireccion asc
        """)
    List<Long> findIdsConPlazoDireccionVencido(@Param("estado") EstadoPedido estado,
                                               @Param("ahora") LocalDateTime ahora);

    @Query("""
        select p.id from Pedido p
        where p.estado = :estado and p.intentosEntregaFallidos >= :intentosMinimos
          and p.fechaEntregaFallida < :fechaLimite
        order by p.fechaEntregaFallida asc
        """)
    List<Long> findIdsConPlazoReprogramacionVencido(@Param("estado") EstadoPedido estado,
                                                    @Param("intentosMinimos") int intentosMinimos,
                                                    @Param("fechaLimite") LocalDateTime fechaLimite);
}

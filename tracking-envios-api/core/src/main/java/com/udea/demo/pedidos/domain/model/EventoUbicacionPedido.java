package com.udea.demo.pedidos.domain.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "eventos_ubicacion_pedido", uniqueConstraints = {
        @UniqueConstraint(name = "uk_evento_ubicacion_uuid", columnNames = "id_evento")
}, indexes = {
        @Index(name = "idx_evento_ubicacion_pedido_fecha", columnList = "id_pedido, fecha_evento")
})
public class EventoUbicacionPedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "id_evento", nullable = false, unique = true, length = 36)
    private String idEvento;
    @Column(name = "id_pedido", nullable = false)
    private Long pedidoId;
    @Column(name = "numero_tracking", nullable = false, length = 50)
    private String numeroTracking;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EtapaUbicacion etapa;
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_pedido", nullable = false, length = 40)
    private EstadoPedido estadoPedido;
    @Column(name = "ciudad_id")
    private Long ciudadId;
    @Column(name = "ciudad_nombre", nullable = false, length = 100)
    private String ciudad;
    @Column(name = "oficina_id")
    private Long oficinaId;
    @Column(name = "oficina_nombre", length = 120)
    private String oficina;
    @Column(name = "punto_entrega_id")
    private Long puntoEntregaId;
    @Column(name = "punto_entrega_nombre", length = 120)
    private String puntoEntrega;
    @Column(length = 250)
    private String direccion;
    @Column(name = "vehiculo_id", length = 80)
    private String vehiculoId;
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigenUbicacion origen;
    @Column(name = "fecha_evento", nullable = false)
    private LocalDateTime fechaEvento;
    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;
    @Column(name = "corrige_evento_id")
    private Long corrigeEventoId;

    protected EventoUbicacionPedido() {}

    public EventoUbicacionPedido(String idEvento, Long pedidoId, String numeroTracking, EtapaUbicacion etapa,
                                 EstadoPedido estadoPedido, Long ciudadId, String ciudad, Long oficinaId,
                                 String oficina, Long puntoEntregaId, String puntoEntrega, String direccion,
                                 String vehiculoId, Long usuarioId, OrigenUbicacion origen,
                                 LocalDateTime fechaEvento, Long corrigeEventoId) {
        this.idEvento = idEvento;
        this.pedidoId = pedidoId;
        this.numeroTracking = numeroTracking;
        this.etapa = etapa;
        this.estadoPedido = estadoPedido;
        this.ciudadId = ciudadId;
        this.ciudad = ciudad;
        this.oficinaId = oficinaId;
        this.oficina = oficina;
        this.puntoEntregaId = puntoEntregaId;
        this.puntoEntrega = puntoEntrega;
        this.direccion = direccion;
        this.vehiculoId = vehiculoId;
        this.usuarioId = usuarioId;
        this.origen = origen;
        this.fechaEvento = fechaEvento;
        this.fechaRegistro = LocalDateTime.now();
        this.corrigeEventoId = corrigeEventoId;
    }

    public Long getId() { return id; }
    public String getIdEvento() { return idEvento; }
    public Long getPedidoId() { return pedidoId; }
    public String getNumeroTracking() { return numeroTracking; }
    public EtapaUbicacion getEtapa() { return etapa; }
    public EstadoPedido getEstadoPedido() { return estadoPedido; }
    public Long getCiudadId() { return ciudadId; }
    public String getCiudad() { return ciudad; }
    public Long getOficinaId() { return oficinaId; }
    public String getOficina() { return oficina; }
    public Long getPuntoEntregaId() { return puntoEntregaId; }
    public String getPuntoEntrega() { return puntoEntrega; }
    public String getDireccion() { return direccion; }
    public String getVehiculoId() { return vehiculoId; }
    public Long getUsuarioId() { return usuarioId; }
    public OrigenUbicacion getOrigen() { return origen; }
    public LocalDateTime getFechaEvento() { return fechaEvento; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public Long getCorrigeEventoId() { return corrigeEventoId; }
}

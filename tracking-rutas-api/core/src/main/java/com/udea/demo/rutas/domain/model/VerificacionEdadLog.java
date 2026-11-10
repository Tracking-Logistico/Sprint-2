package com.udea.demo.rutas.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.Objects;

/**
 * HU-13 (V25, CA-6): resultado de la verificación de edad de un envío.
 *
 * <ul>
 *   <li><b>Minimización.</b> Solo tiene cuatro campos: envío, conductor, resultado y fecha. No hay campo
 *       para el documento, la fecha de nacimiento, imágenes ni texto libre.</li>
 *   <li><b>Un registro por envío.</b> La PK es id_pedido.</li>
 *   <li><b>Inmutable.</b> Usa {@link Immutable} y columnas no actualizables. En la BD lo refuerzan los triggers
 *       trg_verificacion_edad_inmutable y trg_verificacion_edad_sin_truncate.</li>
 *   <li><b>{@link Persistable}.</b> Con un id asignado, {@code save} haría un merge y podría ocultar un
 *       duplicado. Así un objeto nuevo siempre genera un INSERT, y un segundo registro del mismo envío
 *       falla con una violación de PK que la aplicación debe tratar.</li>
 * </ul>
 */
@Entity
@Immutable
@Table(name = "verificacion_edad_log")
public class VerificacionEdadLog implements Persistable<Long> {

    @Id
    @Column(name = "id_pedido", nullable = false, updatable = false)
    private Long pedidoId;

    @Column(name = "id_usuario_conductor", nullable = false, updatable = false)
    private Long conductorUsuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado", nullable = false, updatable = false, length = 10)
    private ResultadoVerificacionEdad resultado;

    /** Instante en UTC. La columna es TIMESTAMP WITH TIME ZONE. */
    @Column(name = "fecha_verificacion", nullable = false, updatable = false)
    private Instant fechaVerificacion;

    @Transient
    private boolean nuevo;

    protected VerificacionEdadLog() {
    }

    private VerificacionEdadLog(Long pedidoId, Long conductorUsuarioId, ResultadoVerificacionEdad resultado,
                                Instant fechaVerificacion) {
        this.pedidoId = pedidoId;
        this.conductorUsuarioId = conductorUsuarioId;
        this.resultado = resultado;
        this.fechaVerificacion = fechaVerificacion;
        this.nuevo = true;
    }

    /**
     * Crea el registro. Que el conductor esté asignado al envío y que el envío requiera la verificación lo
     * comprueba el servicio de aplicación. El trigger trg_verificacion_edad_validar lo vuelve a comprobar en la BD.
     */
    public static VerificacionEdadLog registrar(Long pedidoId, Long conductorUsuarioId,
                                                ResultadoVerificacionEdad resultado, Instant ahora) {
        Objects.requireNonNull(pedidoId, "pedidoId");
        Objects.requireNonNull(conductorUsuarioId, "conductorUsuarioId");
        Objects.requireNonNull(resultado, "resultado");
        Objects.requireNonNull(ahora, "ahora");
        return new VerificacionEdadLog(pedidoId, conductorUsuarioId, resultado, ahora);
    }

    @PostPersist
    @PostLoad
    void marcarPersistido() {
        this.nuevo = false;
    }

    @Override
    public Long getId() {
        return pedidoId;
    }

    @Override
    public boolean isNew() {
        return nuevo;
    }

    public Long getPedidoId() { return pedidoId; }
    public Long getConductorUsuarioId() { return conductorUsuarioId; }
    public ResultadoVerificacionEdad getResultado() { return resultado; }
    public Instant getFechaVerificacion() { return fechaVerificacion; }
}

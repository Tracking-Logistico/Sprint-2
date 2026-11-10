package com.udea.demo.rutas.interfaces.persistence;

import com.udea.demo.rutas.domain.model.VerificacionEdadLog;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * HU-13: acceso al log de verificación de edad. Expone a propósito solo las operaciones de alta y consulta.
 * Al no extender JpaRepository, no hay métodos de borrado ni de actualización masiva, porque el log es append-only.
 */
public interface VerificacionEdadLogRepository extends Repository<VerificacionEdadLog, Long> {

    /** Inserta y hace flush. El log debe escribirse antes de cerrar la parada o cambiar el estado del envío (V25). */
    VerificacionEdadLog saveAndFlush(VerificacionEdadLog verificacion);

    /** Resultado del envío, si existe. La clave es id_pedido. */
    Optional<VerificacionEdadLog> findById(Long pedidoId);

    boolean existsById(Long pedidoId);
}

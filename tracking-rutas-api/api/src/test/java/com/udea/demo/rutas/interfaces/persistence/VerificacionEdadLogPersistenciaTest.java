package com.udea.demo.rutas.interfaces.persistence;

import com.udea.demo.rutas.domain.model.ResultadoVerificacionEdad;
import com.udea.demo.rutas.domain.model.VerificacionEdadLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integración JPA sobre H2 (perfil test): mapeo de la tabla de V25, un registro por envío e inmutabilidad
 * desde JPA. Los triggers de PostgreSQL (conductor asignado, flag 18+, append-only y bloqueo de ENTREGADO sin
 * CUMPLE) se prueban con scripts/probar-migracion.sh 25. Los ids son sintéticos y no existen en H2 claves
 * foráneas hacia pedidos.
 */
@SpringBootTest(properties = {"app.api.role=rutas", "app.messaging.rabbitmq.enabled=false",
        "spring.flyway.enabled=false"})
@ActiveProfiles("test")
@DisplayName("HU-13 Persistencia de verificacion_edad_log")
class VerificacionEdadLogPersistenciaTest {
    private static final long PEDIDO = 990_001L;
    private static final long OTRO_PEDIDO = 990_002L;
    private static final long CONDUCTOR = 880_001L;

    @Autowired private VerificacionEdadLogRepository verificaciones;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM verificacion_edad_log WHERE id_pedido IN (?, ?)", PEDIDO, OTRO_PEDIDO);
    }

    @Test
    @DisplayName("Guarda y recupera el resultado con el instante UTC exacto")
    void guardaYRecupera() {
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.MICROS);
        verificaciones.saveAndFlush(VerificacionEdadLog.registrar(PEDIDO, CONDUCTOR, ResultadoVerificacionEdad.CUMPLE, ahora));

        VerificacionEdadLog leido = verificaciones.findById(PEDIDO).orElseThrow();
        assertThat(leido.getConductorUsuarioId()).isEqualTo(CONDUCTOR);
        assertThat(leido.getResultado()).isEqualTo(ResultadoVerificacionEdad.CUMPLE);
        assertThat(leido.getFechaVerificacion()).isEqualTo(ahora);
        assertThat(leido.isNew()).isFalse();
        assertThat(verificaciones.existsById(PEDIDO)).isTrue();
        assertThat(verificaciones.existsById(OTRO_PEDIDO)).isFalse();
    }

    @Test
    @DisplayName("Negativo: un segundo resultado del mismo envío falla con violación de PK, no se sobrescribe")
    void segundoResultadoRechazado() {
        verificaciones.saveAndFlush(VerificacionEdadLog.registrar(PEDIDO, CONDUCTOR, ResultadoVerificacionEdad.CUMPLE, Instant.now()));

        assertThatThrownBy(() -> verificaciones.saveAndFlush(
                VerificacionEdadLog.registrar(PEDIDO, CONDUCTOR, ResultadoVerificacionEdad.NO_CUMPLE, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(verificaciones.findById(PEDIDO).orElseThrow().getResultado())
                .isEqualTo(ResultadoVerificacionEdad.CUMPLE);
    }

    @Test
    @DisplayName("Negativo: volver a guardar una entidad ya persistida no modifica el registro (@Immutable)")
    void entidadPersistidaNoSeActualiza() {
        verificaciones.saveAndFlush(VerificacionEdadLog.registrar(OTRO_PEDIDO, CONDUCTOR, ResultadoVerificacionEdad.NO_CUMPLE, Instant.now()));
        VerificacionEdadLog leido = verificaciones.findById(OTRO_PEDIDO).orElseThrow();

        verificaciones.saveAndFlush(leido);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM verificacion_edad_log WHERE id_pedido = ?",
                Integer.class, OTRO_PEDIDO)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT resultado FROM verificacion_edad_log WHERE id_pedido = ?",
                String.class, OTRO_PEDIDO)).isEqualTo("NO_CUMPLE");
    }

    @Test
    @DisplayName("Minimización: la tabla generada solo tiene las cuatro columnas permitidas")
    void soloCuatroColumnas() {
        List<String> columnas = jdbc.queryForList("SELECT lower(column_name) FROM information_schema.columns "
                + "WHERE lower(table_name) = 'verificacion_edad_log' ORDER BY column_name", String.class);
        assertThat(columnas).containsExactly("fecha_verificacion", "id_pedido", "id_usuario_conductor", "resultado");
    }
}

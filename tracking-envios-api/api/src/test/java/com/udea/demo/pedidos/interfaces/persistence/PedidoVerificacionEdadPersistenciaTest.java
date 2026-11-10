package com.udea.demo.pedidos.interfaces.persistence;

import com.udea.demo.pedidos.domain.model.Pedido;
import com.udea.demo.pedidos.domain.model.Prioridad;
import com.udea.demo.pedidos.domain.model.TipoServicio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integración JPA sobre H2 (perfil test): persistencia de pedidos.requiere_verificacion_edad (V25, CA-4).
 * El trigger que impone la misma regla en PostgreSQL se prueba con scripts/probar-migracion.sh 25.
 * Todos los datos son sintéticos.
 */
@SpringBootTest(properties = {"app.api.role=pedidos", "app.messaging.rabbitmq.enabled=false",
        "spring.flyway.enabled=false"})
@ActiveProfiles("test")
@DisplayName("HU-13 Persistencia del flag de verificación de edad en pedidos")
class PedidoVerificacionEdadPersistenciaTest {

    @Autowired private PedidoRepository pedidos;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM pedidos WHERE numero_pedido LIKE 'HU13-%'");
    }

    private static Pedido solicitado() {
        return Pedido.recibir(1L, "Origen sintético 1", "Medellín", "050001", "Destino sintético 2", "Medellín",
                "050002", "Paquete sintético", 1.0, 10.0, 10.0, 10.0, TipoServicio.ESTANDAR,
                "HU13-" + UUID.randomUUID(), Prioridad.MEDIA, "Destinatario Sintético", "3000000000",
                "Remitente Sintético", "remitente@ejemplo.invalid", "3000000001");
    }

    @Test
    @DisplayName("Un envío sin marcar se guarda con requiere_verificacion_edad = FALSE")
    void porDefectoFalso() {
        Pedido guardado = pedidos.saveAndFlush(solicitado());
        assertThat(jdbc.queryForObject("SELECT requiere_verificacion_edad FROM pedidos WHERE id_pedido = ?",
                Boolean.class, guardado.getId())).isFalse();
    }

    @Test
    @DisplayName("El remitente marca 18+ al solicitar y el valor persiste")
    void marcaYPersiste() {
        Pedido pedido = solicitado();
        pedido.definirVerificacionEdad(true);
        Long id = pedidos.saveAndFlush(pedido).getId();

        assertThat(pedidos.findById(id).orElseThrow().isRequiereVerificacionEdad()).isTrue();
    }

    @Test
    @DisplayName("Negativo: después de la validación, el flag recargado de la BD ya no se puede cambiar")
    void inmutableTrasValidarYRecargar() {
        Pedido pedido = solicitado();
        pedido.definirVerificacionEdad(true);
        Long id = pedidos.saveAndFlush(pedido).getId();

        Pedido validado = pedidos.findById(id).orElseThrow();
        validado.validar(true, null, "Validación sintética", 99L);
        pedidos.saveAndFlush(validado);

        Pedido recargado = pedidos.findById(id).orElseThrow();
        assertThatThrownBy(() -> recargado.definirVerificacionEdad(false)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT requiere_verificacion_edad FROM pedidos WHERE id_pedido = ?",
                Boolean.class, id)).isTrue();
    }

    @Test
    @DisplayName("Negativo: dos ediciones concurrentes del flag no se pisan (bloqueo optimista con @Version)")
    void edicionConcurrenteRechazada() {
        Long id = pedidos.saveAndFlush(solicitado()).getId();
        Pedido copiaA = pedidos.findById(id).orElseThrow();
        Pedido copiaB = pedidos.findById(id).orElseThrow();

        copiaA.definirVerificacionEdad(true);
        pedidos.saveAndFlush(copiaA);

        copiaB.definirVerificacionEdad(false);
        assertThatThrownBy(() -> pedidos.saveAndFlush(copiaB))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(pedidos.findById(id).orElseThrow().isRequiereVerificacionEdad()).isTrue();
    }
}

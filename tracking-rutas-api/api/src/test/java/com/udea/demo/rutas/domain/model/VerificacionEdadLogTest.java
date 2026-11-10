package com.udea.demo.rutas.domain.model;

import com.udea.demo.rutas.interfaces.persistence.VerificacionEdadLogRepository;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HU-13 VerificacionEdadLog: solo el resultado, inmutable y sin datos del documento")
class VerificacionEdadLogTest {
    private static final Instant AHORA = Instant.parse("2026-11-07T15:30:00Z");

    @Test
    @DisplayName("Registra envío, conductor, resultado e instante UTC")
    void registra() {
        VerificacionEdadLog log = VerificacionEdadLog.registrar(10L, 20L, ResultadoVerificacionEdad.CUMPLE, AHORA);
        assertThat(log.getPedidoId()).isEqualTo(10L);
        assertThat(log.getId()).isEqualTo(10L);
        assertThat(log.getConductorUsuarioId()).isEqualTo(20L);
        assertThat(log.getResultado()).isEqualTo(ResultadoVerificacionEdad.CUMPLE);
        assertThat(log.getFechaVerificacion()).isEqualTo(AHORA);
        assertThat(log.isNew()).isTrue();
    }

    @Test
    @DisplayName("Después de persistirse deja de ser nuevo, así un save posterior nunca vuelve a insertarlo en silencio")
    void dejaDeSerNuevo() {
        VerificacionEdadLog log = VerificacionEdadLog.registrar(10L, 20L, ResultadoVerificacionEdad.NO_CUMPLE, AHORA);
        log.marcarPersistido();
        assertThat(log.isNew()).isFalse();
    }

    @Test
    @DisplayName("Negativo: los cuatro datos son obligatorios")
    void obligatorios() {
        assertThatThrownBy(() -> VerificacionEdadLog.registrar(null, 20L, ResultadoVerificacionEdad.CUMPLE, AHORA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> VerificacionEdadLog.registrar(10L, null, ResultadoVerificacionEdad.CUMPLE, AHORA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> VerificacionEdadLog.registrar(10L, 20L, null, AHORA))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> VerificacionEdadLog.registrar(10L, 20L, ResultadoVerificacionEdad.CUMPLE, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Minimización: la entidad persiste exactamente cuatro campos, ninguno de documento ni de nacimiento")
    void soloCuatroCamposPersistentes() {
        assertThat(Arrays.stream(VerificacionEdadLog.class.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()) && !f.isAnnotationPresent(Transient.class))
                .map(Field::getName))
                .containsExactlyInAnyOrder("pedidoId", "conductorUsuarioId", "resultado", "fechaVerificacion");
    }

    @Test
    @DisplayName("Negativo: no hay setters ni, en el repositorio, operaciones de borrado o actualización")
    void sinMutacion() {
        assertThat(Arrays.stream(VerificacionEdadLog.class.getMethods()).map(Method::getName))
                .noneMatch(nombre -> nombre.startsWith("set"));
        assertThat(Arrays.stream(VerificacionEdadLogRepository.class.getMethods()).map(Method::getName))
                .noneMatch(nombre -> nombre.startsWith("delete") || nombre.startsWith("update")
                        || nombre.startsWith("remove"));
    }

    @Test
    @DisplayName("Solo CUMPLE permite cerrar la entrega como ENTREGADO")
    void permiteEntrega() {
        assertThat(ResultadoVerificacionEdad.CUMPLE.permiteEntrega()).isTrue();
        assertThat(ResultadoVerificacionEdad.NO_CUMPLE.permiteEntrega()).isFalse();
        assertThat(ResultadoVerificacionEdad.values()).hasSize(2);
    }
}

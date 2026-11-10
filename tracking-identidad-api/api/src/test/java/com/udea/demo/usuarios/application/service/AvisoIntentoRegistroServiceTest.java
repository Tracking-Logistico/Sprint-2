package com.udea.demo.usuarios.application.service;

import com.udea.demo.usuarios.domain.model.Rol;
import com.udea.demo.usuarios.domain.model.Usuario;
import com.udea.demo.usuarios.interfaces.services.EmailServiceI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("HU-13 AvisoIntentoRegistroService: aviso al titular sin revelar nada a quien se registra")
class AvisoIntentoRegistroServiceTest {

    private final EmailServiceI email = mock(EmailServiceI.class);
    private final AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-11-07T12:00:00Z"));
    private final Clock reloj = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora.get(); }
    };
    private final AvisoIntentoRegistroService avisos = new AvisoIntentoRegistroService(email, reloj, Duration.ofMinutes(60));

    private static Usuario titular(long id) {
        return Usuario.builder().id(id).email("titular" + id + "@ejemplo.invalid").rol(Rol.CLIENTE).build();
    }

    @Test
    @DisplayName("Envía el aviso al correo del titular, sin incluir la cédula ni datos de quien lo intentó")
    void avisaAlTitular() {
        assertThat(avisos.avisarTitular(titular(1))).isTrue();
        verify(email).enviarNotificacionCambioEstado("titular1@ejemplo.invalid",
                AvisoIntentoRegistroService.ASUNTO, AvisoIntentoRegistroService.CONTENIDO);
        assertThat(AvisoIntentoRegistroService.CONTENIDO).doesNotContainPattern("[0-9]{6,}").doesNotContain("@");
    }

    @Test
    @DisplayName("Negativo anti-abuso: varios intentos seguidos generan un solo aviso por titular dentro del intervalo")
    void limitaFrecuencia() {
        for (int i = 0; i < 20; i++) avisos.avisarTitular(titular(2));
        verify(email, times(1)).enviarNotificacionCambioEstado(anyString(), anyString(), anyString());

        ahora.set(ahora.get().plus(Duration.ofMinutes(61)));
        assertThat(avisos.avisarTitular(titular(2))).isTrue();
        verify(email, times(2)).enviarNotificacionCambioEstado(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("El límite es por titular: otro titular recibe su propio aviso")
    void limitePorTitular() {
        avisos.avisarTitular(titular(3));
        assertThat(avisos.avisarTitular(titular(4))).isTrue();
        verify(email, times(2)).enviarNotificacionCambioEstado(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Negativo sin oráculo: si el correo falla, el error no se propaga y la respuesta no cambia")
    void falloDeCorreoNoSePropaga() {
        doThrow(new RuntimeException("smtp caído")).when(email)
                .enviarNotificacionCambioEstado(anyString(), anyString(), anyString());
        assertThatCode(() -> avisos.avisarTitular(titular(5))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Ignora titulares incompletos sin fallar")
    void titularIncompleto() {
        assertThat(avisos.avisarTitular(null)).isFalse();
        assertThat(avisos.avisarTitular(Usuario.builder().id(6L).build())).isFalse();
        verifyNoInteractions(email);
    }
}

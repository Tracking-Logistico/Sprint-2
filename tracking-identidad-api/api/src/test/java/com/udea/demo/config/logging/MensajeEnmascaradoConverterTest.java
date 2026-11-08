package com.udea.demo.config.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** HU-13 CA-8: enmascarado ****1234 en los logs. Todos los valores son sintéticos. */
@DisplayName("HU-13 MensajeEnmascaradoConverter: cédulas enmascaradas en los logs")
class MensajeEnmascaradoConverterTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "cedula=1000000001 rechazada|cedula=****0001 rechazada",
            "documento 1.000.000.001 recibido|documento ****0001 recibido",
            "dato: 123456|dato: ****3456",
            "telefono 3001234567.|telefono ****4567.",
            "[1000000001,1000000002]|[****0001,****0002]",
            "\"numeroCedula\":\"1000000099\"|\"numeroCedula\":\"****0099\""
    })
    @DisplayName("Enmascara secuencias con forma de documento (6 a 10 dígitos, o con puntos de miles)")
    void enmascara(String original, String esperado) {
        assertThat(MensajeEnmascaradoConverter.enmascarar(original)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "usuarioId=42 pedidoId=12345",
            "eventId=3f1c2b7e-9a1d-4c2e-8b11-1234567890ab",
            "tracking LTABC1234567 creado",
            "fecha 2026-11-07T10:15:30Z",
            "hash a1b2c3d4e5f60718293a4b5c6d7e8f90",
            "epoch 1731000000000",
            "ip 127.0.0.1 puerto 8081",
            "version 0.0.1-SNAPSHOT"
    })
    @DisplayName("No altera ids cortos, UUID, tracking, fechas, hashes, epoch ni IP")
    void noAltera(String texto) {
        assertThat(MensajeEnmascaradoConverter.enmascarar(texto)).isEqualTo(texto);
    }

    @Test
    @DisplayName("Correo para logs: solo la inicial y el dominio")
    void correo() {
        assertThat(MensajeEnmascaradoConverter.enmascararEmail("ana.sintetica@ejemplo.invalid")).isEqualTo("a***@ejemplo.invalid");
        assertThat(MensajeEnmascaradoConverter.enmascararEmail("no-es-correo")).isEqualTo("***");
        assertThat(MensajeEnmascaradoConverter.enmascararEmail(null)).isEqualTo("***");
    }

    @Test
    @DisplayName("Tolera nulos y textos vacíos")
    void nulos() {
        assertThat(MensajeEnmascaradoConverter.enmascarar(null)).isNull();
        assertThat(MensajeEnmascaradoConverter.enmascarar("")).isEmpty();
    }
}

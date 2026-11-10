package com.udea.demo.usuarios.domain.model;

import com.udea.demo.usuarios.domain.exception.DatoIdentidadInvalidoException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Todas las cédulas son sintéticas: no corresponden a personas reales. */
@DisplayName("HU-13 NumeroCedula: normalización, lista blanca y enmascaramiento")
class NumeroCedulaTest {

    @ParameterizedTest
    @ValueSource(strings = {"1000000001", "1.000.000.001", " 1000000001 ", "1 000 000 001"})
    @DisplayName("Normaliza separadores para que el índice ciego no se pueda eludir cambiando el formato")
    void normalizaSeparadores(String entrada) {
        assertThat(NumeroCedula.de(entrada).valor()).isEqualTo("1000000001");
    }

    @ParameterizedTest
    @ValueSource(strings = {"100000", "9999999999"})
    @DisplayName("Acepta los extremos válidos de 6 y 10 dígitos")
    void aceptaLongitudesLimite(String entrada) {
        assertThat(NumeroCedula.de(entrada).valor()).isEqualTo(entrada);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    @DisplayName("Rechaza una cédula ausente con un mensaje del campo")
    void rechazaAusente(String entrada) {
        assertThatThrownBy(() -> NumeroCedula.de(entrada))
                .isInstanceOf(DatoIdentidadInvalidoException.class)
                .hasMessage("El número de cédula es obligatorio")
                .extracting("campo").isEqualTo("numeroCedula");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12345",                         // demasiado corta
            "12345678901",                   // demasiado larga
            "0123456789",                    // cero inicial
            "12345678a",                     // letras
            "-1234567",                      // signo
            "1234567' OR '1'='1",            // inyección SQL
            "<script>alert(1)</script>",     // XSS
            "123456\u0000789",               // byte nulo
            "\u0661\u0662\u0663\u0664\u0665\u0666\u0667", // dígitos arábigo-índicos (no ASCII)
            "\uFF11\uFF12\uFF13\uFF14\uFF15\uFF16\uFF17", // dígitos de ancho completo
            "1234567\u200B",                // carácter invisible
            "12-345-678",                    // guiones no permitidos
            "1e9999999"                      // notación numérica
    })
    @DisplayName("Rechaza formatos inválidos y entradas maliciosas sin devolver el valor recibido")
    void rechazaFormatoInvalido(String entrada) {
        assertThatThrownBy(() -> NumeroCedula.de(entrada))
                .isInstanceOf(DatoIdentidadInvalidoException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(entrada.strip()));
    }

    @Test
    @DisplayName("Corta entradas sobredimensionadas antes de procesarlas")
    void rechazaEntradaSobredimensionada() {
        String enorme = "1".repeat(10_000);
        assertThatThrownBy(() -> NumeroCedula.de(enorme))
                .isInstanceOf(DatoIdentidadInvalidoException.class)
                .hasMessage("El número de cédula no tiene un formato válido");
    }

    @Test
    @DisplayName("toString y enmascarado solo muestran los últimos 4 dígitos, con longitud fija (CA-8)")
    void enmascara() {
        NumeroCedula larga = NumeroCedula.de("1000004321");
        NumeroCedula corta = NumeroCedula.de("204321");
        assertThat(larga.toString()).isEqualTo("****4321").doesNotContain("1000004321");
        assertThat(corta.enmascarado()).isEqualTo("****4321");
        assertThat(String.valueOf(larga)).isEqualTo(larga.enmascarado());
    }

    @Test
    @DisplayName("Dos formatos de la misma cédula son iguales")
    void igualdadPorValorNormalizado() {
        assertThat(NumeroCedula.de("1.000.000.001")).isEqualTo(NumeroCedula.de("1000000001"))
                .hasSameHashCodeAs(NumeroCedula.de("1000000001"))
                .isNotEqualTo(NumeroCedula.de("1000000002"));
    }
}

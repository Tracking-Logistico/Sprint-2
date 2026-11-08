package com.udea.demo.usuarios.domain.model;

import com.udea.demo.usuarios.domain.exception.DatoIdentidadInvalidoException;

import java.util.regex.Pattern;

/**
 * Número de cédula de ciudadanía colombiana, normalizado y validado con lista blanca.
 *
 * <ul>
 *   <li>Normalización: quita espacios y puntos de miles ("1.234.567" pasa a "1234567"). Así las dos formas
 *       producen el mismo índice ciego y la unicidad no se puede eludir cambiando el formato.</li>
 *   <li>Formato: solo dígitos ASCII, de 6 a 10 y sin cero inicial. Se rechazan dígitos Unicode no ASCII,
 *       letras, signos y cualquier intento de inyección.</li>
 *   <li>{@link #toString()} devuelve la versión enmascarada, así que un log accidental no expone la cédula (CA-8).</li>
 * </ul>
 */
public final class NumeroCedula {
    public static final String CAMPO = "numeroCedula";
    static final int LONGITUD_MAXIMA_ENTRADA = 20;
    private static final Pattern FORMATO = Pattern.compile("[1-9][0-9]{5,9}");
    private static final Pattern SEPARADORES_PERMITIDOS = Pattern.compile("[ .]");
    private static final int DIGITOS_VISIBLES = 4;

    private final String valor;

    private NumeroCedula(String valor) {
        this.valor = valor;
    }

    public static NumeroCedula de(String entrada) {
        if (entrada == null || entrada.isBlank()) {
            throw new DatoIdentidadInvalidoException(CAMPO, "El número de cédula es obligatorio");
        }
        if (entrada.length() > LONGITUD_MAXIMA_ENTRADA) {
            throw new DatoIdentidadInvalidoException(CAMPO, "El número de cédula no tiene un formato válido");
        }
        String normalizada = SEPARADORES_PERMITIDOS.matcher(entrada.strip()).replaceAll("");
        if (!FORMATO.matcher(normalizada).matches()) {
            throw new DatoIdentidadInvalidoException(CAMPO,
                    "El número de cédula debe tener entre 6 y 10 dígitos y no puede empezar por cero");
        }
        return new NumeroCedula(normalizada);
    }

    /** Valor normalizado. Úsalo solo para cifrar o calcular el índice ciego, nunca para logs ni respuestas. */
    public String valor() {
        return valor;
    }

    /**
     * HU-13 CA-8: muestra solo los últimos 4 dígitos (****1234). El prefijo tiene longitud fija para no revelar
     * cuántos dígitos tiene la cédula.
     */
    public String enmascarado() {
        return "****" + valor.substring(valor.length() - DIGITOS_VISIBLES);
    }

    @Override
    public String toString() {
        return enmascarado();
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof NumeroCedula cedula && valor.equals(cedula.valor);
    }

    @Override
    public int hashCode() {
        return valor.hashCode();
    }
}

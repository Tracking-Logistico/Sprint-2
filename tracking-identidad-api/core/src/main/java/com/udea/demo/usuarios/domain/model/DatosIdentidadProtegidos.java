package com.udea.demo.usuarios.domain.model;

import java.util.Objects;

/**
 * Cédula y fecha de nacimiento ya protegidas y listas para persistir. No contiene ningún dato en claro.
 *
 * @param numeroCedulaCifrado    AES-256-GCM, formato "v1:..."
 * @param numeroCedulaHash       índice ciego HMAC-SHA256 en hex (64 caracteres)
 * @param fechaNacimientoCifrada AES-256-GCM, formato "v1:..."
 */
public record DatosIdentidadProtegidos(String numeroCedulaCifrado, String numeroCedulaHash,
                                       String fechaNacimientoCifrada) {
    public DatosIdentidadProtegidos {
        Objects.requireNonNull(numeroCedulaCifrado, "numeroCedulaCifrado");
        Objects.requireNonNull(numeroCedulaHash, "numeroCedulaHash");
        Objects.requireNonNull(fechaNacimientoCifrada, "fechaNacimientoCifrada");
    }

    /** Sin datos en el texto: evita que un log del record exponga cifrados o hashes. */
    @Override
    public String toString() {
        return "DatosIdentidadProtegidos[***]";
    }
}

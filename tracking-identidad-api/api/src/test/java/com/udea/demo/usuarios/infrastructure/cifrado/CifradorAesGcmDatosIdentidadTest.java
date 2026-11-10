package com.udea.demo.usuarios.infrastructure.cifrado;

import com.udea.demo.usuarios.domain.exception.CifradoIdentidadException;
import com.udea.demo.usuarios.domain.model.CampoIdentidad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las claves se generan al azar en cada ejecución: no hay ningún secreto en el código. Las cédulas son sintéticas.
 */
@DisplayName("HU-13 CifradorAesGcmDatosIdentidad: AES-256-GCM e índice ciego HMAC-SHA256")
class CifradorAesGcmDatosIdentidadTest {
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final String CEDULA = "1000000001";

    static String claveAleatoria() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static CifradorAesGcmDatosIdentidad cifrador(String clave) {
        return new CifradorAesGcmDatosIdentidad(clave, true);
    }

    private final CifradorAesGcmDatosIdentidad cifrador = cifrador(claveAleatoria());

    @Test
    @DisplayName("Cifra y descifra sin pérdida, con el formato de la migración V24")
    void idaYVuelta() {
        String cifrado = cifrador.cifrar(CEDULA, CampoIdentidad.NUMERO_CEDULA);
        assertThat(cifrado).matches("^v[0-9]{1,3}:[A-Za-z0-9_-]{40,}$").doesNotContain(CEDULA);
        assertThat(cifrador.descifrar(cifrado, CampoIdentidad.NUMERO_CEDULA)).isEqualTo(CEDULA);

        String fecha = cifrador.cifrar("1990-05-17", CampoIdentidad.FECHA_NACIMIENTO);
        assertThat(fecha).matches("^v[0-9]{1,3}:[A-Za-z0-9_-]{40,}$");
        assertThat(cifrador.descifrar(fecha, CampoIdentidad.FECHA_NACIMIENTO)).isEqualTo("1990-05-17");
    }

    @Test
    @DisplayName("El cifrado no es determinista: con un IV aleatorio el mismo dato nunca se repite")
    void cifradoNoDeterminista() {
        Set<String> cifrados = new HashSet<>();
        for (int i = 0; i < 200; i++) cifrados.add(cifrador.cifrar(CEDULA, CampoIdentidad.NUMERO_CEDULA));
        assertThat(cifrados).hasSize(200);
    }

    @Test
    @DisplayName("El índice ciego es determinista, hex de 64 caracteres y no contiene la cédula")
    void indiceCiegoDeterminista() {
        String indice = cifrador.indiceCiegoCedula(CEDULA);
        assertThat(indice).matches("^[0-9a-f]{64}$").doesNotContain(CEDULA)
                .isEqualTo(cifrador.indiceCiegoCedula(CEDULA))
                .isNotEqualTo(cifrador.indiceCiegoCedula("1000000002"));
    }

    @Test
    @DisplayName("Negativo: sin la clave, el índice ciego no se puede recalcular (no basta con un diccionario sobre la BD)")
    void indiceDependeDeLaClave() {
        assertThat(cifrador(claveAleatoria()).indiceCiegoCedula(CEDULA))
                .isNotEqualTo(cifrador.indiceCiegoCedula(CEDULA));
    }

    @Test
    @DisplayName("La misma clave maestra reproduce las mismas subclaves, aunque la instancia sea otra (reinicio del servicio)")
    void derivacionEstable() {
        String clave = claveAleatoria();
        CifradorAesGcmDatosIdentidad a = cifrador(clave);
        CifradorAesGcmDatosIdentidad b = cifrador(clave);
        assertThat(b.descifrar(a.cifrar(CEDULA, CampoIdentidad.NUMERO_CEDULA), CampoIdentidad.NUMERO_CEDULA))
                .isEqualTo(CEDULA);
        assertThat(b.indiceCiegoCedula(CEDULA)).isEqualTo(a.indiceCiegoCedula(CEDULA));
    }

    @Test
    @DisplayName("Negativo: con otra clave no se puede descifrar")
    void otraClaveNoDescifra() {
        String cifrado = cifrador.cifrar(CEDULA, CampoIdentidad.NUMERO_CEDULA);
        assertThatThrownBy(() -> cifrador(claveAleatoria()).descifrar(cifrado, CampoIdentidad.NUMERO_CEDULA))
                .isInstanceOf(CifradoIdentidadException.class)
                .hasMessageNotContaining(CEDULA);
    }

    @Test
    @DisplayName("Negativo: un texto cifrado manipulado (bit-flip) no supera la verificación de integridad")
    void manipulacionDetectada() {
        String cifrado = cifrador.cifrar(CEDULA, CampoIdentidad.NUMERO_CEDULA);
        byte[] bytes = Base64.getUrlDecoder().decode(cifrado.substring(3));
        bytes[bytes.length / 2] ^= 0x01;
        String manipulado = "v1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        assertThatThrownBy(() -> cifrador.descifrar(manipulado, CampoIdentidad.NUMERO_CEDULA))
                .isInstanceOf(CifradoIdentidadException.class)
                .hasMessageContaining("integridad");
    }

    @Test
    @DisplayName("Negativo: copiar el cifrado de una columna a otra falla porque el AAD lo liga a su campo")
    void intercambioDeColumnasDetectado() {
        String cifradoCedula = cifrador.cifrar("1990010100", CampoIdentidad.NUMERO_CEDULA);
        assertThatThrownBy(() -> cifrador.descifrar(cifradoCedula, CampoIdentidad.FECHA_NACIMIENTO))
                .isInstanceOf(CifradoIdentidadException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1000000001", "v1:", "v1:@@@@", "v1:AAAA", "v2:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "v1:' OR 1=1 --", "V1:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    @DisplayName("Negativo: rechaza cifrados mal formados, en claro, de versión desconocida o con inyección")
    void rechazaCifradosInvalidos(String entrada) {
        assertThatThrownBy(() -> cifrador.descifrar(entrada, CampoIdentidad.NUMERO_CEDULA))
                .isInstanceOf(CifradoIdentidadException.class);
    }

    @Test
    @DisplayName("Negativo: rechaza cifrados sobredimensionados sin intentar decodificarlos")
    void rechazaSobredimensionado() {
        String enorme = "v1:" + "A".repeat(1_000_000);
        assertThatThrownBy(() -> cifrador.descifrar(enorme, CampoIdentidad.NUMERO_CEDULA))
                .isInstanceOf(CifradoIdentidadException.class);
        assertThatThrownBy(() -> cifrador.descifrar(null, CampoIdentidad.NUMERO_CEDULA))
                .isInstanceOf(CifradoIdentidadException.class);
        assertThatThrownBy(() -> cifrador.cifrar(null, CampoIdentidad.NUMERO_CEDULA))
                .isInstanceOf(CifradoIdentidadException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.000.000.001", "0123456", "12345", "abc", "1000000001 "})
    @DisplayName("Negativo: el índice solo acepta cédulas normalizadas, para no generar dos índices de la misma cédula")
    void indiceRequiereNormalizacion(String entrada) {
        assertThatThrownBy(() -> cifrador.indiceCiegoCedula(entrada))
                .isInstanceOf(CifradoIdentidadException.class)
                .hasMessageNotContaining(entrada);
    }

    @Test
    @DisplayName("Negativo: si la clave es obligatoria y falta, la aplicación no arranca (fail-fast)")
    void claveObligatoriaAusente() {
        assertThatThrownBy(() -> new CifradorAesGcmDatosIdentidad("", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IDENTIDAD_CLAVE_MAESTRA");
        assertThatThrownBy(() -> new CifradorAesGcmDatosIdentidad(null, true))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Negativo: rechaza claves cortas (menos de 32 caracteres), aunque el cifrado no sea obligatorio")
    void claveCorta() {
        String corta = "x".repeat(31);
        assertThatThrownBy(() -> new CifradorAesGcmDatosIdentidad(corta, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(corta);
    }

    @Test
    @DisplayName("Negativo: sin clave y con el cifrado opcional, arranca pero falla cerrado en cada operación")
    void sinClaveFallaCerrado() {
        CifradorAesGcmDatosIdentidad sinClave = new CifradorAesGcmDatosIdentidad("", false);
        assertThatThrownBy(() -> sinClave.cifrar(CEDULA, CampoIdentidad.NUMERO_CEDULA))
                .isInstanceOf(CifradoIdentidadException.class);
        assertThatThrownBy(() -> sinClave.indiceCiegoCedula(CEDULA))
                .isInstanceOf(CifradoIdentidadException.class);
        String cifradoAjeno = cifrador.cifrar(CEDULA, CampoIdentidad.NUMERO_CEDULA);
        assertThatThrownBy(() -> sinClave.descifrar(cifradoAjeno, CampoIdentidad.NUMERO_CEDULA))
                .isInstanceOf(CifradoIdentidadException.class);
    }

    @Test
    @DisplayName("Es seguro bajo concurrencia: cada operación usa su propia instancia de Cipher y Mac")
    void concurrente() {
        assertThatCode(() -> java.util.stream.IntStream.range(0, 500).parallel().forEach(i -> {
            String cedula = String.valueOf(1_000_000_000L + i);
            String cifrado = cifrador.cifrar(cedula, CampoIdentidad.NUMERO_CEDULA);
            if (!cedula.equals(cifrador.descifrar(cifrado, CampoIdentidad.NUMERO_CEDULA))) {
                throw new AssertionError("Descifrado incorrecto bajo concurrencia");
            }
        })).doesNotThrowAnyException();
    }
}

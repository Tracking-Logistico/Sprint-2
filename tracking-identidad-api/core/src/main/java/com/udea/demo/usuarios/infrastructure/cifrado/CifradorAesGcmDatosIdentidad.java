package com.udea.demo.usuarios.infrastructure.cifrado;

import com.udea.demo.usuarios.domain.exception.CifradoIdentidadException;
import com.udea.demo.usuarios.domain.model.CampoIdentidad;
import com.udea.demo.usuarios.interfaces.services.CifradorDatosIdentidadI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Cifrado en reposo de la cédula y la fecha de nacimiento (HU-13, CA-8). Solo usa la JCA del JDK, sin
 * dependencias nuevas.
 *
 * <ul>
 *   <li><b>Clave fuera del código.</b> Se lee de {@code IDENTIDAD_CLAVE_MAESTRA}, una variable de entorno de
 *       al menos 32 caracteres aleatorios (por ejemplo {@code openssl rand -base64 32}). De ella se derivan
 *       con HKDF-SHA256 dos subclaves independientes: una de cifrado y otra para el índice ciego.</li>
 *   <li><b>Cifrado.</b> AES-256-GCM con un IV aleatorio de 96 bits por operación y una etiqueta de 128 bits.
 *       El AAD liga el texto cifrado a su columna y a la versión de la clave. El formato es
 *       {@code v1:base64url(iv || cifrado || etiqueta)}.</li>
 *   <li><b>Índice ciego.</b> HMAC-SHA256 de la cédula normalizada, en hex. Es determinista, por eso permite
 *       la unicidad. Sin la clave no se puede recalcular, así que no se puede atacar por diccionario con
 *       solo la BD.</li>
 *   <li><b>Sin clave</b> y con {@code app.identidad.cifrado.obligatorio=true}, que es el valor por defecto,
 *       la aplicación no arranca. Con {@code false} (solo en perfiles de test o h2) arranca, pero cualquier
 *       operación de cifrado falla cerrada.</li>
 * </ul>
 */
@Component
public class CifradorAesGcmDatosIdentidad implements CifradorDatosIdentidadI {
    private static final Logger log = LoggerFactory.getLogger(CifradorAesGcmDatosIdentidad.class);

    static final int LONGITUD_MINIMA_CLAVE = 32;
    static final String VERSION_CLAVE = "v1";
    static final int LONGITUD_MAXIMA_CIFRADO = 255;
    private static final String PREFIJO = VERSION_CLAVE + ":";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / 8;
    private static final String TRANSFORMACION = "AES/GCM/NoPadding";
    private static final String HMAC = "HmacSHA256";
    private static final byte[] SAL_HKDF = "logistrack/hu13/datos-identidad".getBytes(StandardCharsets.UTF_8);
    private static final Pattern CEDULA_NORMALIZADA = Pattern.compile("[1-9][0-9]{5,9}");
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final SecretKeySpec claveCifrado;
    private final SecretKeySpec claveIndiceCiego;

    public CifradorAesGcmDatosIdentidad(
            @Value("${app.identidad.cifrado.clave-maestra:}") String claveMaestra,
            @Value("${app.identidad.cifrado.obligatorio:true}") boolean obligatorio) {
        if (claveMaestra == null || claveMaestra.isBlank()) {
            if (obligatorio) {
                throw new IllegalStateException("Falta la variable de entorno IDENTIDAD_CLAVE_MAESTRA, "
                        + "obligatoria para cifrar los datos de identidad (HU-13)");
            }
            log.warn("Cifrado de datos de identidad sin clave: las operaciones con cédula y fecha de nacimiento "
                    + "fallarán hasta configurar IDENTIDAD_CLAVE_MAESTRA");
            this.claveCifrado = null;
            this.claveIndiceCiego = null;
            return;
        }
        byte[] material = claveMaestra.getBytes(StandardCharsets.UTF_8);
        byte[] prk = null;
        byte[] subclaveCifrado = null;
        byte[] subclaveIndice = null;
        try {
            if (material.length < LONGITUD_MINIMA_CLAVE) {
                throw new IllegalStateException("IDENTIDAD_CLAVE_MAESTRA debe tener al menos "
                        + LONGITUD_MINIMA_CLAVE + " caracteres aleatorios");
            }
            prk = hmac(SAL_HKDF, material);
            subclaveCifrado = expandir(prk, "aes-256-gcm|" + VERSION_CLAVE);
            subclaveIndice = expandir(prk, "indice-ciego-cedula|" + VERSION_CLAVE);
            this.claveCifrado = new SecretKeySpec(subclaveCifrado, "AES");
            this.claveIndiceCiego = new SecretKeySpec(subclaveIndice, HMAC);
        } finally {
            Arrays.fill(material, (byte) 0);
            if (prk != null) Arrays.fill(prk, (byte) 0);
            if (subclaveCifrado != null) Arrays.fill(subclaveCifrado, (byte) 0);
            if (subclaveIndice != null) Arrays.fill(subclaveIndice, (byte) 0);
        }
    }

    @Override
    public String cifrar(String textoClaro, CampoIdentidad campo) {
        if (textoClaro == null || campo == null) {
            throw new CifradoIdentidadException("No se puede cifrar un dato de identidad vacío");
        }
        exigirClaves();
        byte[] iv = new byte[IV_BYTES];
        ALEATORIO.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMACION);
            cipher.init(Cipher.ENCRYPT_MODE, claveCifrado, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad(campo));
            byte[] cifrado = cipher.doFinal(textoClaro.getBytes(StandardCharsets.UTF_8));
            byte[] salida = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
            return PREFIJO + Base64.getUrlEncoder().withoutPadding().encodeToString(salida);
        } catch (GeneralSecurityException ex) {
            throw new CifradoIdentidadException("No fue posible cifrar el dato de identidad", ex);
        }
    }

    @Override
    public String descifrar(String textoCifrado, CampoIdentidad campo) {
        if (textoCifrado == null || campo == null || textoCifrado.length() > LONGITUD_MAXIMA_CIFRADO) {
            throw new CifradoIdentidadException("El dato de identidad protegido no es válido");
        }
        if (!textoCifrado.startsWith(PREFIJO)) {
            throw new CifradoIdentidadException("Versión de clave de identidad no soportada");
        }
        exigirClaves();
        byte[] datos;
        try {
            datos = Base64.getUrlDecoder().decode(textoCifrado.substring(PREFIJO.length()));
        } catch (IllegalArgumentException ex) {
            throw new CifradoIdentidadException("El dato de identidad protegido no es válido");
        }
        if (datos.length <= IV_BYTES + TAG_BYTES) {
            throw new CifradoIdentidadException("El dato de identidad protegido no es válido");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMACION);
            cipher.init(Cipher.DECRYPT_MODE, claveCifrado, new GCMParameterSpec(TAG_BITS, datos, 0, IV_BYTES));
            cipher.updateAAD(aad(campo));
            byte[] claro = cipher.doFinal(datos, IV_BYTES, datos.length - IV_BYTES);
            return new String(claro, StandardCharsets.UTF_8);
        } catch (AEADBadTagException ex) {
            throw new CifradoIdentidadException("El dato de identidad protegido no supera la verificación de integridad");
        } catch (GeneralSecurityException ex) {
            throw new CifradoIdentidadException("No fue posible descifrar el dato de identidad", ex);
        }
    }

    @Override
    public String indiceCiegoCedula(String cedulaNormalizada) {
        if (cedulaNormalizada == null || !CEDULA_NORMALIZADA.matcher(cedulaNormalizada).matches()) {
            throw new CifradoIdentidadException("La cédula debe normalizarse antes de calcular su índice");
        }
        exigirClaves();
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(claveIndiceCiego);
            return HexFormat.of().formatHex(mac.doFinal(("cedula|" + cedulaNormalizada).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new CifradoIdentidadException("No fue posible calcular el índice de la cédula", ex);
        }
    }

    private void exigirClaves() {
        if (claveCifrado == null || claveIndiceCiego == null) {
            throw new CifradoIdentidadException("El cifrado de datos de identidad no está configurado");
        }
    }

    private static byte[] aad(CampoIdentidad campo) {
        return (campo.contexto() + "|" + VERSION_CLAVE).getBytes(StandardCharsets.UTF_8);
    }

    /** HKDF-Expand (RFC 5869) de un solo bloque: devuelve 32 bytes, suficiente para AES-256 y HMAC-SHA256. */
    private static byte[] expandir(byte[] prk, String info) {
        byte[] infoBytes = info.getBytes(StandardCharsets.UTF_8);
        byte[] entrada = Arrays.copyOf(infoBytes, infoBytes.length + 1);
        entrada[infoBytes.length] = 0x01;
        return hmac(prk, entrada);
    }

    private static byte[] hmac(byte[] clave, byte[] datos) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(clave, HMAC));
            return mac.doFinal(datos);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 no disponible en la JVM", ex);
        }
    }
}

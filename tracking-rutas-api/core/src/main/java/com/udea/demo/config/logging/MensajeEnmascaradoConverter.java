package com.udea.demo.config.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HU-13 CA-8 / Ley 1581: red de seguridad en los logs. Reemplaza %m, %msg y %message (ver logback-spring.xml),
 * así que se aplica a TODOS los mensajes de la aplicación y de las librerías.
 *
 * <ul>
 *   <li>Toda secuencia con forma de documento (6 a 10 dígitos seguidos, o con puntos de miles como 1.000.000.001)
 *       se enmascara como ****1234: solo quedan los últimos 4 dígitos.</li>
 *   <li>No toca identificadores con letras (UUID, tracking, hashes hex), fechas, horas ni números cortos (ids).</li>
 *   <li>Es defensa en profundidad: el código no debe registrar cédulas. Esto cubre un descuido o el mensaje de
 *       una librería.</li>
 * </ul>
 * El texto de las excepciones (%ex) no pasa por este conversor; por eso los mensajes de excepción del dominio son fijos.
 */
public class MensajeEnmascaradoConverter extends MessageConverter {
    static final Pattern DOCUMENTO = Pattern.compile(
            "(?<![\\w.\\-])(\\d{1,3}(?:\\.\\d{3}){1,3}|\\d{6,10})(?![\\w\\-]|\\.\\d)");
    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+\\-])[A-Za-z0-9._%+\\-]*@([A-Za-z0-9.\\-]+)");

    @Override
    public String convert(ILoggingEvent evento) {
        return enmascarar(super.convert(evento));
    }

    /** Enmascara secuencias con forma de documento: ****1234. */
    public static String enmascarar(String texto) {
        if (texto == null || texto.isEmpty()) return texto;
        Matcher m = DOCUMENTO.matcher(texto);
        StringBuilder salida = new StringBuilder(texto.length());
        while (m.find()) {
            String digitos = m.group(1).replace(".", "");
            String visibles = digitos.length() >= 4 ? digitos.substring(digitos.length() - 4) : digitos;
            m.appendReplacement(salida, Matcher.quoteReplacement("****" + visibles));
        }
        m.appendTail(salida);
        return salida.toString();
    }

    /** Correo para logs: primera letra y dominio (a***@dominio). */
    public static String enmascararEmail(String email) {
        if (email == null || email.isBlank()) return "***";
        Matcher m = EMAIL.matcher(email.strip());
        return m.matches() ? m.group(1) + "***@" + m.group(2) : "***";
    }
}

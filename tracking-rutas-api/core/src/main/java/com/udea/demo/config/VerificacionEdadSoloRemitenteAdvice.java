package com.udea.demo.config;

import com.udea.demo.pedidos.application.dto.RecibirPedidoRequestDTO;
import com.udea.demo.pedidos.application.dto.SoloCamposDeclarados;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HU-13 CA-4 (13-BE-03): solo el Cliente Remitente define "Verificación de edad obligatoria (18+)".
 *
 * <ol>
 *   <li><b>Flag fuera de su endpoint.</b> En TODOS los cuerpos JSON del servicio, salvo el del remitente
 *       ({@link RecibirPedidoRequestDTO}: crear y corregir, con rol CLIENTE y propiedad verificada en el servicio),
 *       la presencia de {@code requiereVerificacionEdad} (con cualquier grafía y a cualquier profundidad) responde 403.
 *       Así un operador (validar, estado logístico, gestión, incidencias), un conductor (checkpoints) o un
 *       destinatario (seguimiento, reprogramación, dirección) no pueden activarlo ni desactivarlo, ni siquiera
 *       por API directa.</li>
 *   <li><b>Whitelist.</b> En los DTOs con {@link SoloCamposDeclarados}, cualquier propiedad que el record no
 *       declare se rechaza con 400 genérico, para que clienteId, estado, remitenteEmail, etc. no se cuelen.</li>
 * </ol>
 * El cuerpo se limita a {@value #MAX_BYTES} bytes antes de analizarlo; el lote offline más grande (100 eventos)
 * queda muy por debajo. Solo se registra el nombre del DTO de destino, nunca los valores.
 */
@ControllerAdvice
public class VerificacionEdadSoloRemitenteAdvice extends RequestBodyAdviceAdapter {
    private static final Logger log = LoggerFactory.getLogger(VerificacionEdadSoloRemitenteAdvice.class);
    static final int MAX_BYTES = 256 * 1024;
    static final String CAMPO_FLAG = "requiereverificacionedad";
    static final String SOLO_REMITENTE = "Solo el cliente remitente puede definir la verificación de edad (18+)";
    static final String CAMPOS_NO_PERMITIDOS = "La solicitud contiene campos no permitidos";

    private final ObjectMapper mapper;

    public VerificacionEdadSoloRemitenteAdvice(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(MethodParameter parametro, Type tipo, Class<? extends HttpMessageConverter<?>> conversor) {
        return true;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage entrada, MethodParameter parametro, Type tipo,
                                           Class<? extends HttpMessageConverter<?>> conversor) throws IOException {
        byte[] cuerpo;
        try (InputStream in = entrada.getBody()) {
            cuerpo = in.readNBytes(MAX_BYTES + 1);
        }
        if (cuerpo.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE);
        }
        JsonNode json = null;
        try {
            json = cuerpo.length == 0 ? null : mapper.readTree(cuerpo);
        } catch (JacksonException ex) {
            // JSON inválido: lo informa el conversor estándar con su 400 genérico.
        }
        if (json != null) {
            Class<?> destino = tipo instanceof Class<?> c ? c : null;
            if (destino != RecibirPedidoRequestDTO.class && contieneFlag(json)) {
                log.warn("Intento de definir la verificación de edad fuera del flujo del remitente destino={}",
                        destino == null ? "?" : destino.getSimpleName());
                throw new AccessDeniedException(SOLO_REMITENTE);
            }
            if (destino != null && destino.isRecord() && destino.isAnnotationPresent(SoloCamposDeclarados.class)
                    && !soloCamposDeclarados(json, destino)) {
                log.warn("Solicitud con campos no declarados rechazada destino={}", destino.getSimpleName());
                throw new IllegalArgumentException(CAMPOS_NO_PERMITIDOS);
            }
        }
        HttpHeaders cabeceras = entrada.getHeaders();
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(cuerpo); }
            @Override public HttpHeaders getHeaders() { return cabeceras; }
        };
    }

    static boolean contieneFlag(JsonNode nodo) {
        if (nodo == null) return false;
        if (nodo.isObject()) {
            for (Map.Entry<String, JsonNode> propiedad : nodo.properties()) {
                if (CAMPO_FLAG.equals(normalizar(propiedad.getKey())) || contieneFlag(propiedad.getValue())) return true;
            }
        } else if (nodo.isArray()) {
            for (JsonNode elemento : nodo) {
                if (contieneFlag(elemento)) return true;
            }
        }
        return false;
    }

    static boolean soloCamposDeclarados(JsonNode json, Class<?> record) {
        if (!json.isObject()) return true; // el conversor rechazará el tipo incorrecto
        Set<String> declarados = Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName)
                .collect(Collectors.toSet());
        return json.propertyNames().stream().allMatch(declarados::contains);
    }

    static String normalizar(String nombre) {
        return nombre.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").strip();
    }
}

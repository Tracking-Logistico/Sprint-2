package com.udea.demo.config;

import com.udea.demo.usuarios.application.dto.SinCamposIdentidad;
import com.udea.demo.usuarios.domain.exception.DatosIdentidadInmutablesException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * HU-13 CA-3, anti asignación masiva: antes de convertir el cuerpo en un DTO marcado con {@link SinCamposIdentidad},
 * busca en el JSON (en cualquier nivel de anidación) campos de identidad. Si encuentra alguno, responde 409
 * IDENTIDAD_INMUTABLE: "rechaza cualquier intento de modificación con un error controlado", también por API directa.
 *
 * <ul>
 *   <li>La comparación ignora mayúsculas, guiones y guiones bajos: numeroCedula, numero_cedula y NUMERO-CEDULA
 *       son el mismo campo.</li>
 *   <li>La edad NO se rechaza: se ignora (CA-2). El servidor siempre la calcula.</li>
 *   <li>El cuerpo se limita a {@value #MAX_BYTES} bytes antes de analizarlo, para no leer cuerpos enormes en memoria.</li>
 *   <li>Solo se registra el nombre del campo detectado, nunca su valor.</li>
 * </ul>
 */
@ControllerAdvice
public class CamposIdentidadProtegidosAdvice extends RequestBodyAdviceAdapter {
    private static final Logger log = LoggerFactory.getLogger(CamposIdentidadProtegidosAdvice.class);
    static final int MAX_BYTES = 16 * 1024;
    static final String MENSAJE = "La cédula y la fecha de nacimiento no se pueden modificar desde el perfil";
    /** Nombres normalizados (minúsculas, sin '_' ni '-'). */
    static final Set<String> CAMPOS_IDENTIDAD = Set.of(
            "numerocedula", "cedula", "numerodocumento", "documento", "fechanacimiento",
            "numerocedulacifrado", "numerocedulahash", "fechanacimientocifrada",
            "datosidentidadbloqueados", "fechabloqueoidentidad", "perfilincompleto");

    private final ObjectMapper mapper;

    public CamposIdentidadProtegidosAdvice(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(MethodParameter parametro, Type tipo, Class<? extends HttpMessageConverter<?>> conversor) {
        return tipo instanceof Class<?> clase && clase.isAnnotationPresent(SinCamposIdentidad.class);
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
        try {
            buscarCampoIdentidad(mapper.readTree(cuerpo)).ifPresent(campo -> {
                log.warn("Intento de modificar datos de identidad por API directa campo={} destino={}",
                        campo, parametro.getParameterType().getSimpleName());
                throw new DatosIdentidadInmutablesException(MENSAJE);
            });
        } catch (JacksonException ex) {
            // JSON inválido: lo informa el conversor estándar de Spring, con su respuesta genérica de 400.
        }
        HttpHeaders cabeceras = entrada.getHeaders();
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(cuerpo); }
            @Override public HttpHeaders getHeaders() { return cabeceras; }
        };
    }

    static Optional<String> buscarCampoIdentidad(JsonNode nodo) {
        if (nodo == null) return Optional.empty();
        if (nodo.isObject()) {
            for (Map.Entry<String, JsonNode> propiedad : nodo.properties()) {
                String normalizado = normalizar(propiedad.getKey());
                if (CAMPOS_IDENTIDAD.contains(normalizado)) return Optional.of(normalizado);
                Optional<String> anidado = buscarCampoIdentidad(propiedad.getValue());
                if (anidado.isPresent()) return anidado;
            }
        } else if (nodo.isArray()) {
            for (JsonNode elemento : nodo) {
                Optional<String> anidado = buscarCampoIdentidad(elemento);
                if (anidado.isPresent()) return anidado;
            }
        }
        return Optional.empty();
    }

    static String normalizar(String nombre) {
        return nombre.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").strip();
    }
}

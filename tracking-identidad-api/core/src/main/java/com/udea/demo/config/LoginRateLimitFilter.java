package com.udea.demo.config;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rate limit en memoria por IP y por minuto para los endpoints públicos sensibles:
 * <ul>
 *   <li>POST /api/v1/auth/login ({@code app.auth.login-rate-limit-per-minute}, 30 por defecto).</li>
 *   <li>POST /api/v1/clientes/registro ({@code app.auth.registro-rate-limit-per-minute}, 10 por defecto,
 *       agregado en HU-13). Limita el uso del formulario para enumerar o probar cédulas en masa y para
 *       inundar de avisos a los titulares.</li>
 * </ul>
 * Cada endpoint tiene sus propios contadores. El 429 se responde antes de procesar el cuerpo, así que no
 * revela nada de los datos enviados.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {
    static final String RUTA_LOGIN = "/api/v1/auth/login";
    static final String RUTA_REGISTRO = "/api/v1/clientes/registro";
    private static final int MAX_CLAVES = 50_000;

    private final ConcurrentHashMap<String, Ventana> ventanas = new ConcurrentHashMap<>();

    @Value("${app.auth.login-rate-limit-per-minute:30}")
    private int maxPorMinuto;

    @Value("${app.auth.registro-rate-limit-per-minute:10}")
    private int maxRegistrosPorMinuto;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod()) && limitada(request.getRequestURI()));
    }

    private static boolean limitada(String uri) {
        return RUTA_LOGIN.equals(uri) || RUTA_REGISTRO.equals(uri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean registro = RUTA_REGISTRO.equals(request.getRequestURI());
        String clave = (registro ? "registro|" : "login|") + request.getRemoteAddr();
        long minuto = Instant.now().getEpochSecond() / 60;
        if (ventanas.size() > MAX_CLAVES) ventanas.entrySet().removeIf(e -> e.getValue().minuto != minuto);
        Ventana ventana = ventanas.compute(clave, (k, actual) -> {
            if (actual == null || actual.minuto != minuto) return new Ventana(minuto);
            actual.intentos.incrementAndGet();
            return actual;
        });
        if (ventana.intentos.get() > (registro ? maxRegistrosPorMinuto : maxPorMinuto)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(registro
                    ? "{\"error\":\"Demasiadas solicitudes de registro. Intenta nuevamente mas tarde.\"}"
                    : "{\"error\":\"Demasiados intentos de inicio de sesion. Intenta nuevamente mas tarde.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static final class Ventana {
        private final long minuto;
        private final AtomicInteger intentos = new AtomicInteger(1);
        private Ventana(long minuto) { this.minuto = minuto; }
    }
}

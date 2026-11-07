package com.udea.demo.config;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.udea.demo.usuarios.application.service.AutenticacionService;
import com.udea.demo.usuarios.domain.model.Rol;
import com.udea.demo.usuarios.domain.model.EstadoUsuario;
import com.udea.demo.usuarios.interfaces.persistence.SesionUsuarioRepository;

@Component
public class SesionAuthenticationFilter extends OncePerRequestFilter {
    private static final long UMBRAL_ACTUALIZACION_ACTIVIDAD_SEGUNDOS = 60;

    private final SesionUsuarioRepository sesionRepository;
    @Value("${app.auth.client-inactivity-minutes:30}")
    private long minutosInactividadCliente;
    @Value("${app.auth.operational-inactivity-minutes:15}")
    private long minutosInactividadOperativo;

    public SesionAuthenticationFilter(SesionUsuarioRepository sesionRepository) {
        this.sesionRepository = sesionRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = bearer(request);

        String requestUri = request.getRequestURI();
        boolean recursoDba = requestUri.startsWith("/api/v1/admin/")
                || requestUri.startsWith("/api/v1/geografia/")
                || requestUri.matches("^/api/v1/pedidos/\\d+/ubicaciones/?$");
        boolean dbaConApiKey = recursoDba
                && SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                    .anyMatch(a -> "ROLE_DBA".equals(a.getAuthority()));
        if (token != null && !dbaConApiKey) {
            sesionRepository.findByAccessTokenHash(AutenticacionService.hash(token)).ifPresent(sesion -> {
                LocalDateTime ahora = LocalDateTime.now();
                Rol rol = sesion.getUsuario().getRol();
                long minutosInactividad = rol == Rol.CLIENTE
                    ? minutosInactividadCliente : minutosInactividadOperativo;
                boolean vigente = sesion.getRevokedAt() == null
                        && sesion.getAccessTokenExpiresAt().isAfter(ahora)
                        && sesion.getLastActivityAt().plusMinutes(minutosInactividad).isAfter(ahora)
                        && Boolean.TRUE.equals(sesion.getUsuario().getActivo())
                        && (sesion.getUsuario().getEstado() == EstadoUsuario.ACTIVO
                            || sesion.getUsuario().getEstado() == EstadoUsuario.PENDIENTE_ACTIVACION
                            || (rol == Rol.CLIENTE
                                && sesion.getUsuario().getEstado() == EstadoUsuario.PENDIENTE_VERIFICACION));
                if (vigente) {
                    if (Duration.between(sesion.getLastActivityAt(), ahora).getSeconds()
                            > UMBRAL_ACTUALIZACION_ACTIVIDAD_SEGUNDOS) {
                        sesion.setLastActivityAt(ahora);
                        LocalDateTime limite = ahora.plusMinutes(minutosInactividad);
                        if (limite.isAfter(sesion.getRefreshTokenExpiresAt())) {
                            limite = sesion.getRefreshTokenExpiresAt();
                        }
                        sesion.setAccessTokenExpiresAt(limite);
                        sesionRepository.save(sesion);
                    }
                    String authority = sesion.getUsuario().getEstado() == EstadoUsuario.PENDIENTE_ACTIVACION
                            ? "ROLE_PASSWORD_CHANGE" : "ROLE_" + rol.name();
                    var auth = new UsernamePasswordAuthenticationToken(
                            sesion.getUsuario().getEmail(), null,
                            List.of(new SimpleGrantedAuthority(authority)));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            });
        }
        filterChain.doFilter(request, response);
    }

    private String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }
}
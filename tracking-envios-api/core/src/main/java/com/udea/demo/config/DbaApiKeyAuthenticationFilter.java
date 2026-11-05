package com.udea.demo.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class DbaApiKeyAuthenticationFilter extends OncePerRequestFilter {
    @Value("${app.dba.api-key:}")
    private String configuredKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        boolean recursoDba = requestUri.startsWith("/api/v1/admin/")
                || requestUri.startsWith("/api/v1/geografia/")
                || requestUri.matches("^/api/v1/pedidos/\\d+/ubicaciones/?$");
        if (recursoDba) {
            String supplied = request.getHeader("X-DBA-Key");
            if (configuredKey != null && !configuredKey.isBlank() && supplied != null
                    && MessageDigest.isEqual(configuredKey.getBytes(StandardCharsets.UTF_8),
                                             supplied.getBytes(StandardCharsets.UTF_8))) {
                var auth = new UsernamePasswordAuthenticationToken(
                        "dba", null, List.of(new SimpleGrantedAuthority("ROLE_DBA")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }
}

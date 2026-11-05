package com.udea.demo.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private final SesionAuthenticationFilter sesionAuthenticationFilter;
    private final DbaApiKeyAuthenticationFilter dbaApiKeyAuthenticationFilter;
    private final Environment environment;

    public SecurityConfig(SesionAuthenticationFilter sesionAuthenticationFilter,
                          DbaApiKeyAuthenticationFilter dbaApiKeyAuthenticationFilter,
                          Environment environment) {
        this.sesionAuthenticationFilter = sesionAuthenticationFilter;
        this.dbaApiKeyAuthenticationFilter = dbaApiKeyAuthenticationFilter;
        this.environment = environment;
    }

    @Bean
    public FilterRegistrationBean<SesionAuthenticationFilter> sesionFilterRegistration() {
        var registration = new FilterRegistrationBean<>(sesionAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<DbaApiKeyAuthenticationFilter> dbaFilterRegistration() {
        var registration = new FilterRegistrationBean<>(dbaApiKeyAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        boolean esDev = environment.acceptsProfiles(Profiles.of("dev", "local", "test", "h2"));

        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin())
                .contentSecurityPolicy(csp -> csp.policyDirectives("frame-ancestors 'self'")))
            .sessionManagement(session -> session.sessionCreationPolicy(
                    org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, error) -> {
                    response.setStatus(401);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("{\"code\":\"SESION_REQUERIDA\",\"error\":\"Se requiere una sesión válida\"}");
                })
                .accessDeniedHandler((request, response, error) -> {
                    var auth = org.springframework.security.core.context.SecurityContextHolder
                            .getContext().getAuthentication();
                    boolean cambioPassword = auth != null && auth.getAuthorities().stream()
                            .anyMatch(a -> "ROLE_PASSWORD_CHANGE".equals(a.getAuthority()));
                    log.warn("Acceso denegado metodo={} ruta={} roles={} motivo={}",
                            request.getMethod(), request.getRequestURI(),
                            auth == null ? "sin sesión" : auth.getAuthorities(), error.getClass().getSimpleName());
                    response.setStatus(403);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(cambioPassword
                            ? "{\"code\":\"CAMBIO_PASSWORD_REQUERIDO\",\"error\":\"Debes cambiar tu contraseña temporal antes de continuar\"}"
                            : "{\"code\":\"ROL_INSUFICIENTE\",\"error\":\"No tienes permiso para realizar esta acción\"}");
                }))
            .addFilterBefore(dbaApiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(sesionAuthenticationFilter, DbaApiKeyAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> {
                auth.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll();
                auth.requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll();
                if (esDev || environment.getProperty("SWAGGER_ENABLED", Boolean.class, false)) {
                    auth.requestMatchers("/h2-console/**", "/swagger-ui/**",
                            "/swagger-ui.html", "/v3/api-docs/**").permitAll();
                }
                auth.requestMatchers(org.springframework.http.HttpMethod.GET, "/internal/health").permitAll();
                auth.requestMatchers("/error", "/api/v1/auth/login", "/api/v1/auth/refresh",
                                 "/api/v1/auth/password/**", "/api/v1/clientes/registro",
                                 "/api/v1/clientes/verificar", "/api/v1/clientes/verificacion/reenviar").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("DBA")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/geografia/**")
                    .hasAnyRole("CLIENTE", "OPERADOR", "CONDUCTOR", "DBA")
                .requestMatchers("/api/v1/geografia/**").hasAnyRole("OPERADOR", "DBA")
                .requestMatchers("/api/v1/auth/logout").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/pedidos").hasRole("CLIENTE")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/pedidos/*/ubicaciones")
                    .hasAnyRole("OPERADOR", "CONDUCTOR")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/pedidos/*/ubicaciones")
                    .hasAnyRole("CLIENTE", "OPERADOR", "CONDUCTOR", "DBA")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/pedidos/mios/**").hasRole("CLIENTE")
                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/pedidos/*/corregir").hasRole("CLIENTE")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/pedidos/mios/tracking/*/reprogramacion")
                    .hasRole("CLIENTE")
                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/pedidos/mios/tracking/*/direccion")
                    .hasRole("CLIENTE")
                .requestMatchers("/api/v1/pedidos/*/trazabilidad", "/api/v1/pedidos/*/trazabilidad/**")
                    .hasRole("OPERADOR")
                .requestMatchers("/api/v1/pedidos/incidencias/tipos", "/api/v1/pedidos/*/incidencias").hasRole("OPERADOR")
                .requestMatchers("/api/v1/pedidos/pendientes", "/api/v1/pedidos/activables",
                                 "/api/v1/pedidos/validados", "/api/v1/pedidos/transito", "/api/v1/pedidos/despachos",
                                 "/api/v1/pedidos/*/validar", "/api/v1/pedidos/*/activar-tracking",
                                 "/api/v1/pedidos/*/estado-logistico", "/api/v1/pedidos/*/etiqueta")
                    .hasRole("OPERADOR")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/pedidos/*/pausar",
                                 "/api/v1/pedidos/*/reanudar", "/api/v1/pedidos/*/cancelar")
                    .hasAnyRole("CLIENTE", "OPERADOR", "CONDUCTOR")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/pedidos/*/checkpoints",
                                 "/api/v1/pedidos/checkpoints/sincronizacion").hasRole("CONDUCTOR")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/pedidos/checkpoints/mios/**")
                    .hasRole("CONDUCTOR")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/pedidos/checkpoints/pendientes-revision",
                                 "/api/v1/pedidos/novedades").hasRole("OPERADOR")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/pedidos/**")
                    .hasAnyRole("CLIENTE", "OPERADOR", "CONDUCTOR")
                .requestMatchers("/api/v1/rutas/envios-pendientes", "/api/v1/rutas/asignaciones",
                                 "/api/v1/rutas/asignaciones/reasignar", "/api/v1/rutas/asignaciones/lote",
                                 "/api/v1/rutas/asignaciones/*/historial", "/api/v1/rutas/*/orden",
                                 "/api/v1/rutas/conductores-disponibles")
                    .hasRole("OPERADOR")
                .requestMatchers("/api/v1/rutas/mi-ruta", "/api/v1/rutas/mis-notificaciones",
                                 "/api/v1/rutas/mis-notificaciones/*/leer").hasRole("CONDUCTOR")
                .requestMatchers("/api/v1/rutas/conductores/**").hasRole("OPERADOR")
                .requestMatchers("/api/v1/clientes/me", "/api/v1/clientes/me/perfil",
                                 "/api/v1/clientes/*/perfil", "/api/v1/clientes/*")
                    .hasRole("CLIENTE")
                .requestMatchers("/api/v1/usuarios/*/password")
                    .hasAnyRole("CLIENTE", "OPERADOR", "CONDUCTOR", "PASSWORD_CHANGE")
                
                .requestMatchers("/api/v1/notificaciones/**").hasRole("CLIENTE")
                .requestMatchers("/api/v1/conductor/panel/**").hasRole("CONDUCTOR")
                .anyRequest().denyAll();
            });
        return http.build();
    }
}

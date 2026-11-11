package com.udea.orquestador.aplicacion;

import com.udea.orquestador.dominio.Servicio;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnrutadorTest {

    private final Enrutador enrutador = new Enrutador();

    static Stream<Arguments> rutasHaciaEnvios() {
        return Stream.of(
                Arguments.of("/api/v1/pedidos", Servicio.ENVIOS),
                Arguments.of("/api/v1/pedidos/", Servicio.ENVIOS),
                Arguments.of("/api/v1/pedidos/mios", Servicio.ENVIOS),
                Arguments.of("/api/v1/pedidos/24/trazabilidad/exportar", Servicio.ENVIOS)
        );
    }

    static Stream<Arguments> rutasHaciaRutas() {
        return Stream.of(
                Arguments.of("/api/v1/rutas", Servicio.RUTAS),
                Arguments.of("/api/v1/rutas/mi-ruta", Servicio.RUTAS),
                Arguments.of("/api/v1/conductor/panel", Servicio.RUTAS),
                Arguments.of("/api/v1/conductor/panel/entregas", Servicio.RUTAS)
        );
    }

    static Stream<Arguments> rutasHaciaIdentidad() {
        return Stream.of(
                Arguments.of("/api/v1/auth", Servicio.IDENTIDAD),
                Arguments.of("/api/v1/auth/login", Servicio.IDENTIDAD),
                Arguments.of("/api/v1/clientes", Servicio.IDENTIDAD),
                Arguments.of("/api/v1/clientes/registro", Servicio.IDENTIDAD),
                Arguments.of("/api/v1/usuarios", Servicio.IDENTIDAD),
                Arguments.of("/api/v1/usuarios/perfil", Servicio.IDENTIDAD),
                Arguments.of("/api/v1/notificaciones", Servicio.IDENTIDAD),
                Arguments.of("/api/v1/notificaciones/preferencias", Servicio.IDENTIDAD),
                Arguments.of("/api/v1/admin", Servicio.IDENTIDAD),
                Arguments.of("/api/v1/assistant/query", Servicio.IDENTIDAD),
                Arguments.of("/api/v1/admin/usuarios", Servicio.IDENTIDAD),
                Arguments.of("/swagger-ui.html", Servicio.IDENTIDAD),
                Arguments.of("/swagger-ui", Servicio.IDENTIDAD),
                Arguments.of("/swagger-ui/index.html", Servicio.IDENTIDAD),
                Arguments.of("/v3/api-docs", Servicio.IDENTIDAD),
                Arguments.of("/v3/api-docs/swagger-config", Servicio.IDENTIDAD)
        );
    }

    static Stream<String> rutasSinServicio() {
        return Stream.of(
                "/api/v1/pedidos-extra",
                "/api/v1/otra-ruta",
                "/internal/admin",
                "/internal/health",
                "/health",
                "/api/v1",
                ""
        );
    }

    @ParameterizedTest
    @MethodSource("rutasHaciaEnvios")
    void resuelvePedidosHaciaEnvios(String ruta, Servicio esperado) {
        Optional<Servicio> resuelto = enrutador.resolver(ruta);
        assertEquals(Optional.of(esperado), resuelto);
    }

    @ParameterizedTest
    @MethodSource("rutasHaciaRutas")
    void resuelveRutasYPanelHaciaRutas(String ruta, Servicio esperado) {
        Optional<Servicio> resuelto = enrutador.resolver(ruta);
        assertEquals(Optional.of(esperado), resuelto);
    }

    @ParameterizedTest
    @MethodSource("rutasHaciaIdentidad")
    void resuelveAuthClientesUsuariosAdminYSwaggerHaciaIdentidad(String ruta, Servicio esperado) {
        Optional<Servicio> resuelto = enrutador.resolver(ruta);
        assertEquals(Optional.of(esperado), resuelto);
    }

    @ParameterizedTest
    @MethodSource("rutasSinServicio")
    void noResuelveRutasFueraDeLosPrefijos(String ruta) {
        assertTrue(enrutador.resolver(ruta).isEmpty());
    }

    @Test
    void noConfundePrefijoConTextoParecido() {
        assertEquals(Optional.empty(), enrutador.resolver("/api/v1/authentication"));
        assertEquals(Optional.empty(), enrutador.resolver("/api/v1/rutas-activas"));
        assertEquals(Optional.empty(), enrutador.resolver("/api/v1/conductor"));
    }
}

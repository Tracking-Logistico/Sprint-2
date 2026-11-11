package com.udea.orquestador.aplicacion;

import com.udea.orquestador.dominio.Servicio;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public final class Enrutador {
    public Optional<Servicio> resolver(String ruta) {
        if (coincide(ruta, "/api/v1/pedidos")) return Optional.of(Servicio.ENVIOS);
        if (coincide(ruta, "/api/v1/rutas") || coincide(ruta, "/api/v1/conductor/panel")) {
            return Optional.of(Servicio.RUTAS);
        }
        if (coincide(ruta, "/api/v1/auth") || coincide(ruta, "/api/v1/clientes")
                || coincide(ruta, "/api/v1/usuarios") || coincide(ruta, "/api/v1/notificaciones")
                || coincide(ruta, "/api/v1/admin") || coincide(ruta, "/api/v1/assistant")) return Optional.of(Servicio.IDENTIDAD);
        if (ruta.equals("/swagger-ui.html") || coincide(ruta, "/swagger-ui")
                || coincide(ruta, "/v3/api-docs")) return Optional.of(Servicio.IDENTIDAD);
        return Optional.empty();
    }

    private boolean coincide(String ruta, String prefijo) {
        return ruta.equals(prefijo) || ruta.startsWith(prefijo + "/");
    }
}

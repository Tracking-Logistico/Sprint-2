package com.udea.demo.pedidos.infrastructure.controller;

import com.udea.demo.pedidos.application.anomalias.GestionAnomaliasService;
import com.udea.demo.pedidos.domain.anomalias.*;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.udea.demo.pedidos.application.anomalias.AnomaliasDTO.*;

@RestController
@RequestMapping("/api/v1/pedidos")
@ConditionalOnProperty(name="app.anomalias.enabled",havingValue="true")
public class AnomaliasController {
    private final GestionAnomaliasService servicio;
    public AnomaliasController(GestionAnomaliasService servicio) { this.servicio=servicio; }

    @GetMapping("/anomalias")
    public Pagina listar(@RequestParam(defaultValue="ACTIVA") EstadoAnomalia estado,
            @RequestParam(required=false) TipoAnomalia tipo,@RequestParam(defaultValue="0") int pagina,
            @RequestParam(defaultValue="20") int tamano) {
        return servicio.listar(estado,tipo,pagina,tamano);
    }

    @GetMapping("/anomalias/recepciones-pendientes")
    public List<Llegada> pendientes(@RequestParam(defaultValue="0") int pagina) {
        return servicio.recepcionesPendientes(pagina);
    }

    @GetMapping("/anomalias/{id}")
    public Anomalia detalle(@PathVariable long id) { return servicio.detalle(id); }

    @PostMapping("/anomalias/{id}/cierre")
    public Anomalia cerrar(@PathVariable long id,@Valid @RequestBody Cerrar dto) { return servicio.cerrar(id,dto); }

    @GetMapping("/anomalias/parametros")
    public Parametros parametros(@RequestHeader(value="X-DBA-Key",required=false) String clave) {
        return servicio.parametros(clave);
    }

    @PutMapping("/anomalias/parametros")
    public Parametros parametros(@Valid @RequestBody CambiarParametros dto,
            @RequestHeader(value="X-DBA-Key",required=false) String clave) {
        return servicio.cambiar(dto,clave);
    }

    @PostMapping("/{id}/recepcion-oficina")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmar(@PathVariable long id,@Valid @RequestBody Recepcion dto) {
        servicio.confirmarRecepcion(id,dto);
    }

    @GetMapping("/mios/tracking/{tracking}/alertas")
    public List<AlertaCliente> alertas(@PathVariable String tracking) {
        if (tracking.length()>50 || !tracking.matches("[A-Za-z0-9-]+")) throw new IllegalArgumentException("Tracking inválido");
        return servicio.alertasCliente(tracking);
    }
}

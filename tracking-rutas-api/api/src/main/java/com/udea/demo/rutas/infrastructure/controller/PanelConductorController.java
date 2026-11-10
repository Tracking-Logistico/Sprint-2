package com.udea.demo.rutas.infrastructure.controller;

import com.udea.demo.rutas.application.dto.*;
import com.udea.demo.rutas.application.service.RegistroResultadoEntregaService;
import com.udea.demo.rutas.application.service.SincronizacionEntregasService;
import com.udea.demo.rutas.application.service.VerificacionEdadService;
import jakarta.validation.Valid;
import com.udea.demo.rutas.application.dto.ParadaPanelDTO;
import com.udea.demo.rutas.application.dto.ProgresoPanelDTO;
import com.udea.demo.rutas.application.dto.SiguienteParadaDTO;
import com.udea.demo.rutas.interfaces.services.PanelConductorServiceI;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/conductor/panel")
public class PanelConductorController {

    private final PanelConductorServiceI panel;
    private final RegistroResultadoEntregaService registro;
    private final SincronizacionEntregasService sincronizacion;
    private final VerificacionEdadService verificacionEdad;

    public PanelConductorController(PanelConductorServiceI panel, RegistroResultadoEntregaService registro,
            SincronizacionEntregasService sincronizacion, VerificacionEdadService verificacionEdad) {
        this.panel = panel;
        this.registro = registro;
        this.sincronizacion = sincronizacion;
        this.verificacionEdad = verificacionEdad;
    }

    /**
     * HU-13 CA-5/6/7: "Mayor de edad verificado" (CUMPLE) o "Verificación no aprobada" (NO_CUMPLE). Solo lo puede usar
     * el conductor asignado a la parada pendiente (rol CONDUCTOR por SecurityConfig y propiedad del recurso en el
     * servicio). Solo se registra el resultado, nunca datos ni imágenes del documento.
     */
    @PostMapping("/entregas/{pedidoId}/verificacion-edad")
    public ResponseEntity<VerificacionEdadResponseDTO> registrarVerificacionEdad(@PathVariable Long pedidoId,
            @Valid @RequestBody VerificacionEdadRequestDTO solicitud) {
        return ResponseEntity.ok(verificacionEdad.registrar(pedidoId, solicitud));
    }

    @GetMapping("/ruta")
    public ResponseEntity<RutaPanelConductorDTO> ruta() { return ResponseEntity.ok(panel.ruta()); }

    @GetMapping("/catalogo-novedades")
    public ResponseEntity<List<NovedadConductorDTO>> catalogoNovedades() {
        // Solo códigos que producen el resultado esperado por el formulario del conductor.
        return ResponseEntity.ok(List.of(
                new NovedadConductorDTO("CLIENTE_AUSENTE", "ENTREGA_FALLIDA", "Cliente ausente"),
                new NovedadConductorDTO("PAQUETE_RECHAZADO", "DEVOLUCION_AL_REMITENTE", "Paquete rechazado")
        ));
    }

    @PostMapping("/entregas/sincronizacion")
    public ResponseEntity<SincronizacionEntregasDTO.Respuesta> sincronizar(
            @Valid @RequestBody SincronizacionEntregasDTO.Solicitud solicitud) {
        return ResponseEntity.ok(sincronizacion.sincronizar(solicitud));
    }

    @PostMapping("/entregas/{pedidoId}/resultado")
    public ResponseEntity<ResultadoEntregaResponseDTO> registrarResultado(@PathVariable Long pedidoId,
            @Valid @RequestBody ResultadoEntregaRequestDTO evento) {
        return ResponseEntity.ok(registro.registrar(pedidoId, evento));
    }

    @GetMapping("/progreso")
    public ResponseEntity<ProgresoPanelDTO> progreso() {
        return ResponseEntity.ok(panel.progreso());
    }

    @GetMapping("/entregas")
    public ResponseEntity<List<ParadaPanelDTO>> entregas() {
        return ResponseEntity.ok(panel.entregasAsignadas());
    }

    @GetMapping("/entregas/{pedidoId}")
    public ResponseEntity<DetalleEntregaDTO> detalle(@PathVariable Long pedidoId) {
        return ResponseEntity.ok(panel.detalleEntrega(pedidoId));
    }

    @GetMapping("/siguiente")
    public ResponseEntity<SiguienteParadaDTO> siguiente() {
        return ResponseEntity.of(java.util.Optional.ofNullable(panel.siguienteParada()));
    }
}
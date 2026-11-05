package com.udea.demo.pedidos.infrastructure.controller;

import com.udea.demo.pedidos.application.dto.*;
import com.udea.demo.pedidos.interfaces.services.PedidoServiceI;
import com.udea.demo.pedidos.application.service.TrazabilidadUbicacionService;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import com.udea.demo.pedidos.domain.model.EstadoPedido;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/pedidos")
public class PedidoController {
    private final PedidoServiceI pedidoService;
    private TrazabilidadUbicacionService trazabilidad;

    public PedidoController(PedidoServiceI pedidoService) { this.pedidoService = pedidoService; }

    @Autowired
    public void setTrazabilidad(TrazabilidadUbicacionService trazabilidad) {
        this.trazabilidad = trazabilidad;
    }

    @PostMapping
    public ResponseEntity<PedidoResponseDTO> recibirPedido(@Valid @RequestBody RecibirPedidoRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.recibir(dto));
    }

    @PutMapping("/{id}/corregir")
    public ResponseEntity<PedidoResponseDTO> corregir(@PathVariable Long id, @Valid @RequestBody RecibirPedidoRequestDTO dto) {
        return ResponseEntity.ok(pedidoService.corregir(id, dto));
    }

    @GetMapping("/mios")
    public ResponseEntity<Page<PedidoClienteResponseDTO>> listarMios(
            @RequestParam(required = false) EstadoPedido estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @PageableDefault(size = 20, sort = "fechaCreacion", direction = org.springframework.data.domain.Sort.Direction.DESC)
            Pageable pageable) {
        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fechaDesde no puede ser posterior a fechaHasta");
        }
        if (pageable.getSort().stream().anyMatch(order ->
                !List.of("fechaCreacion", "estado").contains(order.getProperty()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se permite ordenar por fechaCreacion o estado");
        }
        LocalDateTime desde = fechaDesde == null ? null : fechaDesde.atStartOfDay();
        LocalDateTime hasta = fechaHasta == null ? null : fechaHasta.plusDays(1).atStartOfDay();
        return ResponseEntity.ok(pedidoService.listarMios(estado, desde, hasta, limitar(pageable)));
    }

    @GetMapping("/pendientes")
    public ResponseEntity<Page<PedidoResponseDTO>> listarPendientes(@PageableDefault(size = 20) Pageable pageable) { return ResponseEntity.ok(pedidoService.listarPendientes(limitar(pageable))); }

    @GetMapping("/transito")
    public ResponseEntity<Page<PedidoResponseDTO>> listarEnTransito(@PageableDefault(size = 20) Pageable pageable) { return ResponseEntity.ok(pedidoService.listarEnTransito(limitar(pageable))); }

    @GetMapping("/despachos")
    public ResponseEntity<List<PedidoResponseDTO>> listarDespachos() { return ResponseEntity.ok(pedidoService.listarDespachos()); }

    @GetMapping("/validados")
    public ResponseEntity<Page<PedidoResponseDTO>> listarValidados(@PageableDefault(size = 20) Pageable pageable) { return ResponseEntity.ok(pedidoService.listarValidados(limitar(pageable))); }

    @GetMapping("/activables")
    public ResponseEntity<Page<PedidoResponseDTO>> listarActivables(@PageableDefault(size = 20) Pageable pageable) { return ResponseEntity.ok(pedidoService.listarActivables(limitar(pageable))); }

    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponseDTO> obtenerPedido(@PathVariable Long id) { return ResponseEntity.ok(pedidoService.obtener(id)); }

    @GetMapping("/{id}/historial")
    public ResponseEntity<List<HistorialPedidoResponseDTO>> historial(@PathVariable Long id) { return ResponseEntity.ok(pedidoService.historial(id)); }

    @GetMapping("/tracking/{numeroTracking}")
    public ResponseEntity<PedidoResponseDTO> obtenerPorTracking(@PathVariable String numeroTracking) {
        return ResponseEntity.ok(pedidoService.obtenerPorTracking(numeroTracking));
    }

    @GetMapping("/mios/tracking/{numeroTracking}")
    public ResponseEntity<SeguimientoClienteResponseDTO> obtenerSeguimientoMio(@PathVariable String numeroTracking) {
        SeguimientoClienteResponseDTO seguimiento = pedidoService.obtenerSeguimientoCliente(numeroTracking);
        return ResponseEntity.ok(seguimiento.conUbicaciones(trazabilidad.consultarCliente(numeroTracking)));
    }

    @PutMapping("/{id}/validar")
    public ResponseEntity<PedidoResponseDTO> validarPedido(@PathVariable Long id,
            @Valid @RequestBody ValidarPedidoRequestDTO dto) {
        return ResponseEntity.ok(pedidoService.validar(id, dto));
    }

    @PutMapping("/{id}/activar-tracking")
    public ResponseEntity<PedidoResponseDTO> activarTracking(@PathVariable Long id) {
        return ResponseEntity.ok(pedidoService.activarTracking(id));
    }

    @PutMapping("/{id}/estado-logistico")
    public ResponseEntity<PedidoResponseDTO> cambiarEstado(@PathVariable Long id,
            @Valid @RequestBody CambiarEstadoLogisticoRequestDTO dto) {
        return ResponseEntity.ok(pedidoService.cambiarEstadoLogistico(id, dto));
    }

    @PostMapping("/{id}/etiqueta")
    public ResponseEntity<EtiquetaEnvioResponseDTO> generarEtiqueta(@PathVariable Long id) {
        return ResponseEntity.ok(pedidoService.generarEtiqueta(id));
    }
    private Pageable limitar(Pageable pageable) {
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 50);
        return PageRequest.of(pageable.getPageNumber(), size, pageable.getSort());
    }
}

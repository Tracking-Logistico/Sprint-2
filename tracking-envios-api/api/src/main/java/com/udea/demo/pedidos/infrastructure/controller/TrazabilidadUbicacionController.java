package com.udea.demo.pedidos.infrastructure.controller;

import com.udea.demo.pedidos.application.dto.RegistrarUbicacionRequestDTO;
import com.udea.demo.pedidos.application.dto.UbicacionResponseDTO;
import com.udea.demo.pedidos.application.service.TrazabilidadUbicacionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/pedidos/{pedidoId}/ubicaciones")
public class TrazabilidadUbicacionController {
    private final TrazabilidadUbicacionService trazabilidad;

    public TrazabilidadUbicacionController(TrazabilidadUbicacionService trazabilidad) {
        this.trazabilidad = trazabilidad;
    }

    @PostMapping
    public ResponseEntity<UbicacionResponseDTO> registrar(@PathVariable Long pedidoId,
            @Valid @RequestBody RegistrarUbicacionRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(trazabilidad.registrar(pedidoId, dto));
    }

    @GetMapping
    public List<UbicacionResponseDTO> consultar(@PathVariable Long pedidoId) {
        return trazabilidad.consultar(pedidoId);
    }
}

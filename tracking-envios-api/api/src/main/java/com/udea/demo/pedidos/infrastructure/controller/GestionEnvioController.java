package com.udea.demo.pedidos.infrastructure.controller;

import com.udea.demo.pedidos.application.dto.GestionEnvioRequestDTO;
import com.udea.demo.pedidos.application.dto.GestionEnvioResponseDTO;
import com.udea.demo.pedidos.application.service.GestionEnvioService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/pedidos")
public class GestionEnvioController {
    private final GestionEnvioService gestionEnvio;

    public GestionEnvioController(GestionEnvioService gestionEnvio) {
        this.gestionEnvio = gestionEnvio;
    }

    @PostMapping("/{id}/pausar")
    public ResponseEntity<GestionEnvioResponseDTO> pausar(@PathVariable Long id,
            @Valid @RequestBody GestionEnvioRequestDTO dto) {
        return ResponseEntity.ok(gestionEnvio.pausar(id, dto));
    }

    @PostMapping("/{id}/reanudar")
    public ResponseEntity<GestionEnvioResponseDTO> reanudar(@PathVariable Long id) {
        return ResponseEntity.ok(gestionEnvio.reanudar(id));
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<GestionEnvioResponseDTO> cancelar(@PathVariable Long id,
            @Valid @RequestBody GestionEnvioRequestDTO dto) {
        return ResponseEntity.ok(gestionEnvio.cancelar(id, dto));
    }
}

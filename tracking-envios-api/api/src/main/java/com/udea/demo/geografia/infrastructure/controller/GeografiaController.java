package com.udea.demo.geografia.infrastructure.controller;

import com.udea.demo.geografia.application.dto.*;
import com.udea.demo.geografia.application.service.GeografiaService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/geografia")
public class GeografiaController {
    private final GeografiaService geografia;

    public GeografiaController(GeografiaService geografia) {
        this.geografia = geografia;
    }

    @GetMapping("/ciudades")
    public List<CiudadResponseDTO> ciudades(@RequestParam(defaultValue = "false") boolean incluirInactivas) {
        return geografia.ciudades(incluirInactivas);
    }

    @PostMapping("/ciudades")
    public ResponseEntity<CiudadResponseDTO> crearCiudad(@Valid @RequestBody CiudadRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(geografia.crearCiudad(dto));
    }

    @PutMapping("/ciudades/{id}")
    public CiudadResponseDTO actualizarCiudad(@PathVariable long id, @Valid @RequestBody CiudadRequestDTO dto) {
        return geografia.actualizarCiudad(id, dto);
    }

    @PatchMapping("/ciudades/{id}/activa")
    public ResponseEntity<Void> activarCiudad(@PathVariable long id, @RequestParam boolean activa) {
        geografia.activarCiudad(id, activa);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/oficinas")
    public List<OficinaResponseDTO> oficinas(@RequestParam(required = false) Long ciudadId,
                                             @RequestParam(defaultValue = "false") boolean incluirInactivas) {
        return geografia.oficinas(ciudadId, incluirInactivas);
    }

    @PostMapping("/oficinas")
    public ResponseEntity<OficinaResponseDTO> crearOficina(@Valid @RequestBody OficinaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(geografia.crearOficina(dto));
    }

    @PutMapping("/oficinas/{id}")
    public OficinaResponseDTO actualizarOficina(@PathVariable long id, @Valid @RequestBody OficinaRequestDTO dto) {
        return geografia.actualizarOficina(id, dto);
    }

    @PatchMapping("/oficinas/{id}/activa")
    public ResponseEntity<Void> activarOficina(@PathVariable long id, @RequestParam boolean activa) {
        geografia.activarOficina(id, activa);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/puntos-entrega")
    public List<PuntoEntregaResponseDTO> puntos(@RequestParam(required = false) Long ciudadId,
                                                @RequestParam(required = false) Long oficinaId,
                                                @RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return geografia.puntos(ciudadId, oficinaId, incluirInactivos);
    }

    @PostMapping("/puntos-entrega")
    public ResponseEntity<PuntoEntregaResponseDTO> crearPunto(@Valid @RequestBody PuntoEntregaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(geografia.crearPunto(dto));
    }

    @PutMapping("/puntos-entrega/{id}")
    public PuntoEntregaResponseDTO actualizarPunto(@PathVariable long id,
            @Valid @RequestBody PuntoEntregaRequestDTO dto) {
        return geografia.actualizarPunto(id, dto);
    }

    @PatchMapping("/puntos-entrega/{id}/activo")
    public ResponseEntity<Void> activarPunto(@PathVariable long id, @RequestParam boolean activo) {
        geografia.activarPunto(id, activo);
        return ResponseEntity.noContent().build();
    }
}

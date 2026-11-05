package com.udea.demo.geografia.application.dto;

public record OficinaResponseDTO(Long id, String nombre, Long ciudadId, String ciudad, String departamento,
                                 String direccion, String telefono, String email, boolean activa) {}

package com.udea.demo.geografia.application.dto;

public record PuntoEntregaResponseDTO(Long id, String nombre, Long ciudadId, String ciudad,
                                      Long oficinaId, String oficina, String direccion,
                                      String descripcion, boolean activo) {}

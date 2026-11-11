package com.udea.demo.pedidos.infrastructure.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice(assignableTypes=AnomaliasController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ErroresAnomaliasAdvice {
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String,String>> denegado() { return error(HttpStatus.FORBIDDEN); }

    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String,String>> entrada() { return error(HttpStatus.BAD_REQUEST); }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> estado(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("error","No fue posible completar la solicitud"));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String,String>> conflicto() { return error(HttpStatus.CONFLICT); }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,String>> inesperado() { return error(HttpStatus.INTERNAL_SERVER_ERROR); }

    private ResponseEntity<Map<String,String>> error(HttpStatus estado) {
        return ResponseEntity.status(estado).body(Map.of("error","No fue posible completar la solicitud"));
    }
}

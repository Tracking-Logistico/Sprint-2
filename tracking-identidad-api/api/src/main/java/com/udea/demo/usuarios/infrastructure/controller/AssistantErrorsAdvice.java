package com.udea.demo.usuarios.infrastructure.controller;

import com.udea.demo.usuarios.application.assistant.AssistantUnavailableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.Map;

@RestControllerAdvice(assignableTypes=AssistantController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AssistantErrorsAdvice {
    @ExceptionHandler({IllegalArgumentException.class,MethodArgumentNotValidException.class,HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String,String>> invalid() { return error(HttpStatus.BAD_REQUEST); }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String,String>> forbidden() { return error(HttpStatus.FORBIDDEN); }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,String>> status(ResponseStatusException ex) { return error(ex.getStatusCode()); }
    @ExceptionHandler(org.springframework.core.task.TaskRejectedException.class)
    public ResponseEntity<Map<String,String>> busy() { return error(HttpStatus.TOO_MANY_REQUESTS); }
    @ExceptionHandler(AssistantUnavailableException.class)
    public ResponseEntity<Map<String,String>> unavailable() { return error(HttpStatus.SERVICE_UNAVAILABLE); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,String>> unexpected() { return error(HttpStatus.INTERNAL_SERVER_ERROR); }
    private ResponseEntity<Map<String,String>> error(HttpStatusCode status) {
        return ResponseEntity.status(status).body(Map.of("error","No fue posible completar la consulta. Intenta nuevamente."));
    }
}

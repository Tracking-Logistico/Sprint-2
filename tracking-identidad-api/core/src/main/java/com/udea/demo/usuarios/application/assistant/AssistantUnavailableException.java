package com.udea.demo.usuarios.application.assistant;

public class AssistantUnavailableException extends RuntimeException {
    public AssistantUnavailableException() { super("Asistente temporalmente no disponible"); }
}

package com.udea.demo.usuarios.application.assistant;

import jakarta.validation.constraints.*;
import java.util.UUID;

public final class AssistantDTO {
    private AssistantDTO() {}
    public enum Intent { PERFIL, PAQUETE, FUERA_DE_ALCANCE }
    public enum Result { RESUELTA, FUERA_DE_ALCANCE, SIN_DATOS, ERROR }
    public enum Origin { WEB, MOVIL, QR, OFFLINE }
    public record Query(@NotBlank @Size(max=500) String texto, UUID sessionId,
                        @NotNull UUID solicitudId, @NotNull Origin origen) {}
    public record Answer(UUID sessionId, Result resultado, String mensaje) {}
    public record Prepared(Intent intent, String target, String anonymousText) {}
    public record Lookup(Result result, String text) {}
}

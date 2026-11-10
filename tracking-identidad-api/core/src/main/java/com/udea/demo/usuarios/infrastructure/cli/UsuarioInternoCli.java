package com.udea.demo.usuarios.infrastructure.cli;

import com.udea.demo.usuarios.application.dto.CrearUsuarioInternoCommand;
import com.udea.demo.usuarios.application.dto.EditarUsuarioInternoCommand;
import com.udea.demo.usuarios.application.dto.ResultadoCreacionUsuarioInterno;
import com.udea.demo.usuarios.domain.model.Rol;
import com.udea.demo.usuarios.interfaces.services.UsuarioInternoServiceI;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import jakarta.validation.Validator;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.api.role", havingValue = "usuarios")
@Component
@Profile("cli")
public class UsuarioInternoCli implements ApplicationRunner {
    static final String MENSAJE_CONFLICTO = "Los datos entran en conflicto con un usuario existente";

    private final UsuarioInternoServiceI service;
    private final Validator validator;

    public UsuarioInternoCli(UsuarioInternoServiceI service, Validator validator) {
        this.service = service;
        this.validator = validator;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (args.getNonOptionArgs().isEmpty()) {
            System.out.println("Uso: crear | editar | desactivar");
            return;
        }

        String comando = args.getNonOptionArgs().get(0);
        Map<String, String> opts = extraerOpciones(args);

        try {
            switch (comando) {
                case "crear" -> crear(opts);
                case "editar" -> editar(opts);
                case "desactivar" -> desactivar(opts);
                default -> System.out.println("Comando desconocido: " + comando);
            }
        } catch (DataIntegrityViolationException ex) {
            // El mensaje del driver incluye los valores en conflicto (por ejemplo, el hash de la cédula).
            // No se imprime ni se propaga la causa.
            System.err.println("ERROR: " + MENSAJE_CONFLICTO);
            throw new IllegalStateException(MENSAJE_CONFLICTO);
        } catch (RuntimeException ex) {
            System.err.println("ERROR: " + ex.getMessage());
            throw ex;
        }
    }

    private void crear(Map<String, String> o) {
        CrearUsuarioInternoCommand cmd = new CrearUsuarioInternoCommand(
                o.get("nombre"),
                o.get("email"),
                o.get("telefono"),
                Rol.valueOf(o.getOrDefault("rol", "").toUpperCase()),
                o.get("direccion"),
                o.get("licencia"),
                o.get("codigoEmpleado"),
                o.get("numeroCedula"),
                o.get("fechaNacimiento")
        );

        validar(cmd);
        ResultadoCreacionUsuarioInterno r = service.crear(cmd);

        System.out.println("=================================================");
        System.out.println("  USUARIO INTERNO CREADO");
        System.out.println("=================================================");
        System.out.println("  ID:                  " + r.id());
        System.out.println("  Email:               " + r.email());
        System.out.println("  Rol:                 " + r.rol());
        System.out.println("  Password temporal:   " + r.passwordTemporal());
        System.out.println("  Estado:              PENDIENTE_ACTIVACION");
        System.out.println("-------------------------------------------------");
        System.out.println("  Entregue esta contraseña al usuario.");
        System.out.println("  NO se vuelve a mostrar.");
        System.out.println("  El usuario debe cambiarla en su primer inicio de sesión.");
        System.out.println("=================================================");
    }

    private void editar(Map<String, String> o) {
        Long id = Long.valueOf(o.get("id"));

        Rol rol = o.containsKey("rol")
                ? Rol.valueOf(o.get("rol").toUpperCase())
                : null;

        EditarUsuarioInternoCommand cmd = new EditarUsuarioInternoCommand(
                o.get("nombre"),
                o.get("telefono"),
                o.get("direccion"),
                rol,
                o.get("licencia"),
                o.get("codigoEmpleado")
        );

        validar(cmd);
        service.editar(id, cmd);
        System.out.println("Usuario " + id + " actualizado.");
    }

    private void desactivar(Map<String, String> o) {
        Long id = Long.valueOf(o.get("id"));
        service.desactivar(id);
        System.out.println("Usuario " + id + " desactivado.");
    }

    private <T> void validar(T command) {
        var violations = validator.validate(command);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; ")));
        }
    }

    private Map<String, String> extraerOpciones(ApplicationArguments args) {
        Map<String, String> map = new HashMap<>();
        for (String name : args.getOptionNames()) {
            map.put(name, args.getOptionValues(name).get(0));
        }
        return map;
    }
}

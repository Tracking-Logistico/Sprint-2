package com.udea.demo.usuarios.infrastructure.controller;

import com.udea.demo.usuarios.application.dto.ActualizarPerfilRequestDTO;
import com.udea.demo.usuarios.application.dto.ClienteResponseDTO;
import com.udea.demo.usuarios.application.dto.RegistroClienteRequestDTO;
import com.udea.demo.usuarios.application.dto.RegistroClienteResponseDTO;
import com.udea.demo.usuarios.application.dto.CompletarIdentidadRequestDTO;
import com.udea.demo.usuarios.application.dto.IdentidadPerfilResponseDTO;
import com.udea.demo.usuarios.interfaces.services.IdentidadPerfilServiceI;
import com.udea.demo.usuarios.application.dto.UsuarioResponseDTO;
import com.udea.demo.usuarios.interfaces.services.ClienteServiceI;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clientes")
public class ClienteController {

    private final ClienteServiceI clienteService;
    private final IdentidadPerfilServiceI identidadPerfil;

    public ClienteController(ClienteServiceI usuarioService, IdentidadPerfilServiceI identidadPerfil) {
        this.clienteService = usuarioService;
        this.identidadPerfil = identidadPerfil;
    }

    /** HU-13 CA-2 y CA-3: identidad del propio cliente en solo lectura (cédula enmascarada, edad calculada en el servidor). */
    @GetMapping("/me/identidad")
    public ResponseEntity<IdentidadPerfilResponseDTO> obtenerIdentidad() {
        return ResponseEntity.ok(identidadPerfil.consultarPropia());
    }

    /**
     * HU-13: completa la cédula y la fecha de nacimiento de un perfil incompleto (V26). Solo se puede una vez:
     * después responde 409 IDENTIDAD_INMUTABLE. El origen (WEB o MOVIL) es opcional y se usa en la auditoría.
     */
    @PutMapping("/me/identidad")
    public ResponseEntity<IdentidadPerfilResponseDTO> completarIdentidad(
            @Valid @RequestBody CompletarIdentidadRequestDTO dto,
            @RequestHeader(name = "X-Origen", required = false) String origen) {
        return ResponseEntity.ok(identidadPerfil.completarPropia(dto, origen));
    }

    /** HU-13: 201 con un cuerpo neutro idéntico, se haya creado o no la cuenta (anti-enumeración de cédula). */
    @PostMapping("/registro")
    public ResponseEntity<RegistroClienteResponseDTO> registrarCliente(@Valid @RequestBody RegistroClienteRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.registrarCliente(dto));
    }

    @GetMapping("/verificar")
    public ResponseEntity<String> verificarCuenta(@RequestParam String token) {
        clienteService.verificarCuenta(token);
        return ResponseEntity.ok("Correo verificado correctamente. Puedes seguir utilizando tu cuenta.");
    }

    @PostMapping("/verificacion/reenviar")
    public ResponseEntity<String> reenviarVerificacion(@Valid @RequestBody
            com.udea.demo.usuarios.application.dto.SolicitarRestablecimientoPasswordDTO dto) {
        clienteService.reenviarVerificacion(dto.email());
        return ResponseEntity.ok("Si existe una cuenta pendiente, recibirás un enlace de verificación.");
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ResponseEntity<ClienteResponseDTO> obtenerPerfilActual() {
        return ResponseEntity.ok(clienteService.obtenerPerfilActual());
    }

    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/me/perfil")
    public ResponseEntity<UsuarioResponseDTO> actualizarPerfilActual(@Valid @RequestBody ActualizarPerfilRequestDTO dto) {
        return ResponseEntity.ok(clienteService.actualizarPerfilActual(dto));
    }

    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/me")
    public ResponseEntity<Void> desactivarCuentaActual() {
        clienteService.desactivarCuentaActual();
        return ResponseEntity.noContent().build();
    }

    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}/perfil")
    public ResponseEntity<UsuarioResponseDTO> actualizarPerfil(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarPerfilRequestDTO dto,
            Authentication authentication) {
        UsuarioResponseDTO respuesta = clienteService.actualizarPerfil(id, dto, authentication.getName());
        return ResponseEntity.ok(respuesta);
    }

    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivarCuenta(@PathVariable Long id, Authentication authentication) {
        clienteService.desactivarCuentaCliente(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

}
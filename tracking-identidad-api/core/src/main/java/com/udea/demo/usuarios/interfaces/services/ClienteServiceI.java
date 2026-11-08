package com.udea.demo.usuarios.interfaces.services;

import com.udea.demo.usuarios.application.dto.ActualizarPerfilRequestDTO;
import com.udea.demo.usuarios.application.dto.ClienteResponseDTO;
import com.udea.demo.usuarios.application.dto.RegistroClienteRequestDTO;
import com.udea.demo.usuarios.application.dto.RegistroClienteResponseDTO;
import com.udea.demo.usuarios.application.dto.UsuarioResponseDTO;

public interface ClienteServiceI {
    /** Respuesta siempre neutra: no revela si la cédula ya pertenece a otra cuenta (HU-13, 13-BE-01). */
    RegistroClienteResponseDTO registrarCliente(RegistroClienteRequestDTO dto);
    ClienteResponseDTO obtenerPerfilActual();
    UsuarioResponseDTO actualizarPerfilActual(ActualizarPerfilRequestDTO dto);
    void desactivarCuentaActual();
    UsuarioResponseDTO actualizarPerfil(Long id, ActualizarPerfilRequestDTO dto, String usuarioAutenticadoEmail);
    void desactivarCuentaCliente(Long id, String usuarioAutenticadoEmail);
    void verificarCuenta(String token);
    void reenviarVerificacion(String email);
}

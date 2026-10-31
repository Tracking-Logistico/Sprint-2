package com.udea.demo.usuarios.application.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.udea.demo.usuarios.application.dto.LoginRequestDTO;
import com.udea.demo.usuarios.application.dto.LoginResponseDTO;
import com.udea.demo.usuarios.domain.exception.CredencialesInvalidasException;
import com.udea.demo.usuarios.domain.exception.CuentaBloqueadaLoginException;
import com.udea.demo.usuarios.domain.model.EstadoUsuario;
import com.udea.demo.usuarios.domain.model.IntentoInicioSesion;
import com.udea.demo.usuarios.domain.model.Rol;
import com.udea.demo.usuarios.domain.model.Usuario;
import com.udea.demo.usuarios.interfaces.persistence.IntentoInicioSesionRepository;
import com.udea.demo.usuarios.interfaces.persistence.UsuarioRepository;

@Service
public class InicioSesionUseCase {
    private static final Logger log = LoggerFactory.getLogger(InicioSesionUseCase.class);
    private static final int MAX_INTENTOS = 5;
    private final String dummyPasswordHash;
    private final UsuarioRepository usuarioRepository;
    private final IntentoInicioSesionRepository intentoRepository;
    private final PasswordEncoder passwordEncoder;
    private final GestionSesionesUseCase sesiones;

    @Value("${app.auth.failed-attempt-window-minutes:10}")
    private long ventanaIntentosMinutos;
    @Value("${app.auth.lock-minutes:15}")
    private long minutosBloqueo;

    public InicioSesionUseCase(UsuarioRepository usuarioRepository,
            IntentoInicioSesionRepository intentoRepository,
            PasswordEncoder passwordEncoder, GestionSesionesUseCase sesiones) {
        this.usuarioRepository = usuarioRepository;
        this.intentoRepository = intentoRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
        this.sesiones = sesiones;
    }

    @Transactional(noRollbackFor = {CredencialesInvalidasException.class, CuentaBloqueadaLoginException.class})
    public LoginResponseDTO iniciarSesion(LoginRequestDTO request, String ip, String userAgent) {
        LocalDateTime ahora = LocalDateTime.now();
        IntentoInicioSesion bloqueo = intentoRepository
                .findFirstByEmailAndBloqueadoHastaAfterOrderByBloqueadoHastaDesc(request.email(), ahora);
        if (bloqueo != null) {
            throw new CuentaBloqueadaLoginException(bloqueo.getBloqueadoHasta());
        }

        Usuario usuario = usuarioRepository.findByEmail(request.email()).orElse(null);
        String hashParaValidar = usuario == null ? dummyPasswordHash : usuario.getPassword();
        boolean passwordCorrecta = passwordEncoder.matches(request.password(), hashParaValidar);
        boolean estadoLoginValido = usuario != null && (usuario.getEstado() == EstadoUsuario.ACTIVO
                || usuario.getEstado() == EstadoUsuario.PENDIENTE_ACTIVACION
                || (usuario.getRol() == Rol.CLIENTE
                    && usuario.getEstado() == EstadoUsuario.PENDIENTE_VERIFICACION));
        if (usuario == null || !estadoLoginValido
                || !Boolean.TRUE.equals(usuario.getActivo())
                || !passwordCorrecta) {
            registrarFallo(request.email(), usuario, ahora, ip, userAgent);
            throw new CredencialesInvalidasException();
        }

        intentoRepository.deleteByEmail(request.email());
        return sesiones.crearSesion(usuario, ahora);
    }

    private void registrarFallo(String email, Usuario usuario, LocalDateTime ahora,
                                    String ip, String userAgent) {
        LocalDateTime desde = ahora.minusMinutes(ventanaIntentosMinutos);
        long intentos = intentoRepository.countByEmailAndIntentadoEnAfter(email, desde) + 1;
        LocalDateTime bloqueadoHasta = intentos >= MAX_INTENTOS
                ? ahora.plusMinutes(minutosBloqueo) : null;
        intentoRepository.save(new IntentoInicioSesion(email, usuario, ahora, bloqueadoHasta));
        String safeEmail = sanitizarLog(email);
        String safeIp = sanitizarLog(ip);
        String safeUserAgent = sanitizarLog(userAgent);
        log.warn("Intento de inicio de sesión fallido para {} desde {} ({})",
                safeEmail, safeIp, safeUserAgent);
        if (bloqueadoHasta != null) {
            throw new CuentaBloqueadaLoginException(bloqueadoHasta);
        }
    }

    private String sanitizarLog(String entrada) {
        if (entrada == null) {
            return "N/A";
        }
        return entrada.replace('\n', '_').replace('\r', '_').trim();
    }
}

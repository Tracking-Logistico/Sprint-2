package com.udea.demo.usuarios.application.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.udea.demo.usuarios.application.dto.RegistroClienteResponseDTO;
import com.udea.demo.usuarios.domain.model.DatosIdentidadProtegidos;
import org.springframework.security.access.AccessDeniedException;
import com.udea.demo.usuarios.interfaces.services.EmailServiceI;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.udea.demo.usuarios.application.dto.ActualizarPerfilRequestDTO;
import com.udea.demo.usuarios.application.dto.ClienteResponseDTO;
import com.udea.demo.usuarios.application.dto.RegistroClienteRequestDTO;
import com.udea.demo.usuarios.application.dto.UsuarioResponseDTO;
import com.udea.demo.usuarios.domain.model.Cliente;
import com.udea.demo.usuarios.domain.model.EstadoUsuario;
import com.udea.demo.usuarios.domain.model.Rol;
import com.udea.demo.usuarios.domain.model.TokenVerificacion;
import com.udea.demo.usuarios.domain.model.Usuario;
import com.udea.demo.usuarios.interfaces.persistence.ClienteRepository;
import com.udea.demo.usuarios.interfaces.persistence.TokenVerificacionRepository;
import com.udea.demo.usuarios.interfaces.persistence.UsuarioRepository;
import com.udea.demo.usuarios.interfaces.services.ClienteServiceI;

@Service
public class ClienteService implements ClienteServiceI {

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final TokenVerificacionRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailServiceI emailService;
    private final ActorAuthorizationService actorAuthorizationService;
    private final PasswordPolicyService passwordPolicyService;
    private final ProteccionDatosIdentidadService proteccionIdentidad;
    private final AvisoIntentoRegistroService avisoIntentoRegistro;
    private final TransactionTemplate transacciones;

    @Value("${app.verification-url}")
    private String verificationUrl;

    public ClienteService(UsuarioRepository usuarioRepository,
                          ClienteRepository clienteRepository,
                          TokenVerificacionRepository tokenRepository,
                          PasswordEncoder passwordEncoder,
                          EmailServiceI emailService,
                          ActorAuthorizationService actorAuthorizationService,
                          PasswordPolicyService passwordPolicyService,
                          ProteccionDatosIdentidadService proteccionIdentidad,
                          AvisoIntentoRegistroService avisoIntentoRegistro,
                          PlatformTransactionManager transactionManager) {
        this.usuarioRepository = usuarioRepository;
        this.clienteRepository = clienteRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.actorAuthorizationService = actorAuthorizationService;
        this.passwordPolicyService = passwordPolicyService;
        this.proteccionIdentidad = proteccionIdentidad;
        this.avisoIntentoRegistro = avisoIntentoRegistro;
        this.transacciones = new TransactionTemplate(transactionManager);
    }

    /**
     * Registro público de cliente (HU-01A con HU-13 CA-1).
     *
     * <p>Anti-enumeración de la cédula (13-BE-01). La respuesta es siempre {@link RegistroClienteResponseDTO#NEUTRA}:
     * <ul>
     *   <li>Cédula nueva: se crea la cuenta y se envía el correo de verificación.</li>
     *   <li>Cédula de otra cuenta: no se crea nada y se avisa al titular en su propio correo.</li>
     *   <li>Carrera (dos registros simultáneos con la misma cédula): la constraint única de V24 rechaza al
     *       segundo, que recibe la misma respuesta neutra.</li>
     * </ul>
     * La contraseña se codifica con BCrypt en todos los caminos, para que el tiempo de respuesta no delate la
     * existencia. Los errores de formato (cédula mal escrita, fecha irreal o futura) sí se informan por campo,
     * porque se refieren a la entrada y no a otras cuentas.
     */
    @Override
    public RegistroClienteResponseDTO registrarCliente(RegistroClienteRequestDTO dto) {
        if (!dto.password().equals(dto.confirmarPassword())) {
            throw new IllegalArgumentException("Las contraseñas no coinciden");
        }
        passwordPolicyService.validar(dto.password(), dto.email(), dto.nombre());
        if (usuarioRepository.existsByEmail(dto.email())) {
            throw new IllegalArgumentException("El correo ya se encuentra registrado");
        }
        DatosIdentidadProtegidos identidad = proteccionIdentidad.protegerHoy(dto.numeroCedula(), dto.fechaNacimiento());
        String passwordCodificada = passwordEncoder.encode(dto.password());

        Optional<Usuario> titular = usuarioRepository.findByNumeroCedulaHash(identidad.numeroCedulaHash());
        if (titular.isPresent()) {
            avisoIntentoRegistro.avisarTitular(titular.get());
            return RegistroClienteResponseDTO.NEUTRA;
        }
        try {
            transacciones.executeWithoutResult(estado -> crearCuenta(dto, passwordCodificada, identidad));
        } catch (DataIntegrityViolationException ex) {
            Optional<Usuario> ganador = usuarioRepository.findByNumeroCedulaHash(identidad.numeroCedulaHash());
            if (ganador.isEmpty()) throw ex;
            avisoIntentoRegistro.avisarTitular(ganador.get());
        }
        return RegistroClienteResponseDTO.NEUTRA;
    }

    private void crearCuenta(RegistroClienteRequestDTO dto, String passwordCodificada,
                             DatosIdentidadProtegidos identidad) {
        Usuario usuario = Usuario.builder()
                .nombre(dto.nombre())
                .email(dto.email())
                .password(passwordCodificada)
                .telefono(dto.telefono())
                .direccion(dto.direccion())
                .rol(Rol.CLIENTE)
                .estado(EstadoUsuario.PENDIENTE_VERIFICACION)
                .activo(true)
                .aceptoTerminos(dto.aceptoTerminos())
                .versionTerminos(dto.versionTerminos())
                .fechaAceptacionTerminos(LocalDateTime.now())
                .aceptoPoliticaDatos(dto.aceptoPoliticaDatos())
                .versionPoliticaDatos(dto.versionPoliticaDatos())
                .fechaAceptacionPoliticaDatos(LocalDateTime.now())
                .fechaCreacion(LocalDateTime.now())
                .build();
        usuario.registrarDatosIdentidad(identidad, Instant.now());

        Usuario guardado = usuarioRepository.saveAndFlush(usuario);

        Cliente cliente = Cliente.builder()
                .usuario(guardado)
                .ciudad(dto.ciudad())
                .build();
        clienteRepository.save(cliente);

        String tokenUUID = UUID.randomUUID().toString();
        TokenVerificacion tokenVerificacion = TokenVerificacion.builder()
                .token(AutenticacionService.hash(tokenUUID))
                .usuario(guardado)
                .fechaExpiracion(LocalDateTime.now().plusHours(2))
                .build();

        tokenRepository.save(tokenVerificacion);
        emailService.enviarVerificacion(guardado.getEmail(), guardado.getNombre(),
                verificationUrl + (verificationUrl.contains("?") ? "&" : "?") + "token=" + tokenUUID);
    }

    @Override
    @Transactional
    public void reenviarVerificacion(String email) {
        usuarioRepository.findByEmail(email).filter(usuario -> usuario.getRol() == Rol.CLIENTE
                && Boolean.TRUE.equals(usuario.getActivo())
                && usuario.getEstado() == EstadoUsuario.PENDIENTE_VERIFICACION).ifPresent(usuario -> {
            var anterior = tokenRepository.findByUsuarioId(usuario.getId());

            if (anterior.isPresent() && anterior.get().getFechaExpiracion().minusHours(2)
                    .isAfter(LocalDateTime.now().minusMinutes(1))) return;
            TokenVerificacion verificacion = anterior.orElseGet(() -> TokenVerificacion.builder().usuario(usuario).build());
            String token = UUID.randomUUID().toString();
            verificacion.setToken(AutenticacionService.hash(token));
            verificacion.setFechaExpiracion(LocalDateTime.now().plusHours(2));
            tokenRepository.save(verificacion);
            emailService.enviarVerificacion(usuario.getEmail(), usuario.getNombre(),
                    verificationUrl + (verificationUrl.contains("?") ? "&" : "?") + "token=" + token);
        });
    }

    @Override
    @Transactional
    public void verificarCuenta(String token) {
        TokenVerificacion tokenVerificacion = tokenRepository.findByToken(AutenticacionService.hash(token))
                .orElseThrow(() -> new IllegalArgumentException("Token de verificación inválido"));

        if (tokenVerificacion.estaExpirado()) {
            throw new IllegalArgumentException("El token de verificación ha expirado");
        }

        Usuario usuario = tokenVerificacion.getUsuario();
        if (usuario.getRol() != Rol.CLIENTE
                || usuario.getEstado() != EstadoUsuario.PENDIENTE_VERIFICACION
                || !Boolean.TRUE.equals(usuario.getActivo())) {
            throw new IllegalArgumentException("El token ya no corresponde a una cuenta pendiente de verificación");
        }
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuarioRepository.save(usuario);

        tokenRepository.delete(tokenVerificacion);
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponseDTO obtenerPerfilActual() {
        Long clienteId = actorAuthorizationService.clienteActualId();
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente no encontrado"));
        return mapToClienteResponseDTO(cliente);
    }

    @Override
    @Transactional
    public UsuarioResponseDTO actualizarPerfilActual(ActualizarPerfilRequestDTO dto) {
        Usuario usuario = actorAuthorizationService.actorActual();
        return actualizarUsuario(usuario, dto);
    }

    @Override
    @Transactional
    public void desactivarCuentaActual() {
        Usuario usuario = actorAuthorizationService.actorActual();
        if (usuario.getRol() != Rol.CLIENTE) throw new IllegalArgumentException("La cuenta no corresponde a un cliente");
        usuario.setActivo(false);
        usuario.setEstado(EstadoUsuario.INACTIVO);
        usuarioRepository.save(usuario);
    }

    @Override
    @Transactional
    public UsuarioResponseDTO actualizarPerfil(Long id, ActualizarPerfilRequestDTO dto,
                                                String usuarioAutenticadoEmail) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + id));
        validarPropietario(usuario, usuarioAutenticadoEmail);

        actorAuthorizationService.exigirPropietario(usuario.getId(), Rol.CLIENTE);
        return actualizarUsuario(usuario, dto);
    }

    @Override
    @Transactional
    public void desactivarCuentaCliente(Long id, String usuarioAutenticadoEmail) {
        Usuario u = usuarioRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + id));
        validarPropietario(u, usuarioAutenticadoEmail);
        actorAuthorizationService.exigirPropietario(u.getId(), Rol.CLIENTE);
        u.setActivo(false);
        u.setEstado(EstadoUsuario.INACTIVO);
        usuarioRepository.save(u);
    }

    private void validarPropietario(Usuario usuario, String usuarioAutenticadoEmail) {
        if (!usuario.getEmail().equalsIgnoreCase(usuarioAutenticadoEmail)) {
            throw new AccessDeniedException("No tiene autorización para modificar este recurso.");
        }
    }

    private UsuarioResponseDTO actualizarUsuario(Usuario usuario, ActualizarPerfilRequestDTO dto) {
        usuario.setNombre(dto.nombre());
        if (dto.telefono() != null) usuario.setTelefono(dto.telefono());
        if (dto.direccion() != null) usuario.setDireccion(dto.direccion());
        return mapToUsuarioDTO(usuarioRepository.save(usuario));
    }

    private ClienteResponseDTO mapToClienteResponseDTO(Cliente c) {
        return new ClienteResponseDTO(
                c.getId(),
                c.getUsuario().getNombre(),
                c.getUsuario().getEmail(),
                c.getUsuario().getTelefono(),
                c.getUsuario().getDireccion(),
                c.getCiudad(),
                c.getUsuario().getRol(),
                c.getUsuario().getEstado(),
                c.getUsuario().getFechaCreacion()
        );
    }

    private UsuarioResponseDTO mapToUsuarioDTO(Usuario u) {
        return new UsuarioResponseDTO(
                u.getId(), u.getNombre(), u.getEmail(),
                u.getTelefono(), u.getDireccion(),
                u.getRol(), u.getEstado(), u.getFechaCreacion());
    }
}
package com.udea.demo.usuarios.application.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InicioSesionUseCase - autenticación y bloqueo de intentos fallidos")
class InicioSesionUseCaseTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private IntentoInicioSesionRepository intentoRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private GestionSesionesUseCase sesiones;

    private InicioSesionUseCase useCase;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("hash-dummy-generado");
        useCase = new InicioSesionUseCase(usuarioRepository, intentoRepository, passwordEncoder, sesiones);
        ReflectionTestUtils.setField(useCase, "ventanaIntentosMinutos", 10L);
        ReflectionTestUtils.setField(useCase, "minutosBloqueo", 15L);
    }

    @Test
    @DisplayName("Lanza CuentaBloqueadaLoginException si la cuenta ya está bloqueada")
    void iniciarSesion_cuentaBloqueada_lanzaExcepcion() {
        LoginRequestDTO req = new LoginRequestDTO("bloqueado@test.com", "Pass123!");
        IntentoInicioSesion bloqueo = new IntentoInicioSesion("bloqueado@test.com", null, LocalDateTime.now(), LocalDateTime.now().plusMinutes(10));
        when(intentoRepository.findFirstByEmailAndBloqueadoHastaAfterOrderByBloqueadoHastaDesc(eq("bloqueado@test.com"), any()))
                .thenReturn(bloqueo);

        assertThatThrownBy(() -> useCase.iniciarSesion(req, "127.0.0.1", "Mozilla"))
                .isInstanceOf(CuentaBloqueadaLoginException.class);
    }

    @Test
    @DisplayName("Lanza CredencialesInvalidasException y registra fallo si el usuario no existe")
    void iniciarSesion_usuarioNoExiste_registraFallo() {
        LoginRequestDTO req = new LoginRequestDTO("desconocido@test.com", "Pass123!");
        when(intentoRepository.findFirstByEmailAndBloqueadoHastaAfterOrderByBloqueadoHastaDesc(eq("desconocido@test.com"), any()))
                .thenReturn(null);
        when(usuarioRepository.findByEmail("desconocido@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches("Pass123!", "hash-dummy-generado")).thenReturn(false);
        when(intentoRepository.countByEmailAndIntentadoEnAfter(eq("desconocido@test.com"), any())).thenReturn(0L);

        assertThatThrownBy(() -> useCase.iniciarSesion(req, "127.0.0.1", "Mozilla"))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(intentoRepository).save(any(IntentoInicioSesion.class));
        verify(passwordEncoder).matches("Pass123!", "hash-dummy-generado");
        verify(passwordEncoder, times(1)).encode(anyString());
    }

    @Test
    @DisplayName("Lanza CredencialesInvalidasException si la contraseña es incorrecta")
    void iniciarSesion_passwordIncorrecta_lanzaExcepcion() {
        LoginRequestDTO req = new LoginRequestDTO("usuario@test.com", "WrongPass!");
        Usuario usuario = Usuario.builder().email("usuario@test.com").password("hash").rol(Rol.CLIENTE).activo(true).estado(EstadoUsuario.ACTIVO).build();

        when(intentoRepository.findFirstByEmailAndBloqueadoHastaAfterOrderByBloqueadoHastaDesc(eq("usuario@test.com"), any())).thenReturn(null);
        when(usuarioRepository.findByEmail("usuario@test.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("WrongPass!", "hash")).thenReturn(false);
        when(intentoRepository.countByEmailAndIntentadoEnAfter(eq("usuario@test.com"), any())).thenReturn(1L);

        assertThatThrownBy(() -> useCase.iniciarSesion(req, "127.0.0.1", "Mozilla"))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(intentoRepository).save(any(IntentoInicioSesion.class));
    }

    @Test
    @DisplayName("Bloquea la cuenta tras alcanzar el número máximo de intentos fallidos")
    void iniciarSesion_alcanzaMaxIntentos_bloqueaCuenta() {
        LoginRequestDTO req = new LoginRequestDTO("usuario@test.com", "WrongPass!");
        Usuario usuario = Usuario.builder().email("usuario@test.com").password("hash").rol(Rol.CLIENTE).activo(true).estado(EstadoUsuario.ACTIVO).build();

        when(intentoRepository.findFirstByEmailAndBloqueadoHastaAfterOrderByBloqueadoHastaDesc(eq("usuario@test.com"), any())).thenReturn(null);
        when(usuarioRepository.findByEmail("usuario@test.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("WrongPass!", "hash")).thenReturn(false);
        when(intentoRepository.countByEmailAndIntentadoEnAfter(eq("usuario@test.com"), any())).thenReturn(4L); // +1 = 5 (MAX_INTENTOS)

        assertThatThrownBy(() -> useCase.iniciarSesion(req, "127.0.0.1", "Mozilla"))
                .isInstanceOf(CuentaBloqueadaLoginException.class);

        verify(intentoRepository).save(any(IntentoInicioSesion.class));
    }

    @Test
    @DisplayName("Inicia sesión exitosamente, borra intentos previos y crea sesión")
    void iniciarSesion_exitoso_creaSesion() {
        LoginRequestDTO req = new LoginRequestDTO("usuario@test.com", "CorrectPass!");
        Usuario usuario = Usuario.builder().id(5L).email("usuario@test.com").password("hash").rol(Rol.OPERADOR).activo(true).estado(EstadoUsuario.ACTIVO).build();

        when(intentoRepository.findFirstByEmailAndBloqueadoHastaAfterOrderByBloqueadoHastaDesc(eq("usuario@test.com"), any())).thenReturn(null);
        when(usuarioRepository.findByEmail("usuario@test.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("CorrectPass!", "hash")).thenReturn(true);

        LoginResponseDTO loginRes = new LoginResponseDTO("acc", "ref", LocalDateTime.now().plusHours(1), LocalDateTime.now().plusDays(7), Rol.OPERADOR, "/panel/operador", 5L, false);
        when(sesiones.crearSesion(eq(usuario), any())).thenReturn(loginRes);

        LoginResponseDTO res = useCase.iniciarSesion(req, "127.0.0.1", "Mozilla");

        assertThat(res.accessToken()).isEqualTo("acc");
        verify(intentoRepository).deleteByEmail("usuario@test.com");
    }
    @Test
    @DisplayName("Reutiliza el hash dummy y nunca autentica a un usuario inexistente")
    void usuarioInexistenteReutilizaHashSinAutenticar() {
        var request = new LoginRequestDTO("ausente@test.com", "cualquier-entrada");
        when(usuarioRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        when(passwordEncoder.matches(request.password(), "hash-dummy-generado")).thenReturn(true);

        for (int intento = 0; intento < 2; intento++) {
            assertThatThrownBy(() -> useCase.iniciarSesion(request, "127.0.0.1", "test"))
                    .isInstanceOf(CredencialesInvalidasException.class);
        }

        verify(passwordEncoder, times(1)).encode(anyString());
        verify(passwordEncoder, times(2)).matches(request.password(), "hash-dummy-generado");
        verifyNoInteractions(sesiones);
    }

}

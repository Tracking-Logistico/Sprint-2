package com.udea.demo.usuarios.application.service;

import com.udea.demo.usuarios.application.dto.ActualizarPerfilRequestDTO;
import com.udea.demo.usuarios.application.dto.ClienteResponseDTO;
import com.udea.demo.usuarios.application.dto.RegistroClienteRequestDTO;
import com.udea.demo.usuarios.application.dto.RegistroClienteResponseDTO;
import com.udea.demo.usuarios.domain.exception.DatoIdentidadInvalidoException;
import com.udea.demo.usuarios.infrastructure.cifrado.CifradorAesGcmDatosIdentidad;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Spy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.LocalDate;
import java.time.ZoneId;
import com.udea.demo.usuarios.application.dto.UsuarioResponseDTO;
import com.udea.demo.usuarios.domain.model.Cliente;
import com.udea.demo.usuarios.domain.model.EstadoUsuario;
import com.udea.demo.usuarios.domain.model.Rol;
import com.udea.demo.usuarios.domain.model.TokenVerificacion;
import com.udea.demo.usuarios.domain.model.Usuario;
import com.udea.demo.usuarios.interfaces.persistence.ClienteRepository;
import com.udea.demo.usuarios.interfaces.persistence.TokenVerificacionRepository;
import com.udea.demo.usuarios.interfaces.persistence.UsuarioRepository;
import com.udea.demo.usuarios.interfaces.services.EmailServiceI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
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
@DisplayName("ClienteService - registro y gestión de perfil de clientes")
class ClienteServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private TokenVerificacionRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailServiceI emailService;
    @Mock private ActorAuthorizationService actorAuthorizationService;
    @Mock private PasswordPolicyService passwordPolicyService;
    @Mock private AvisoIntentoRegistroService avisoIntentoRegistro;
    @Mock private PlatformTransactionManager transactionManager;
    /** Servicio real con una clave aleatoria por ejecución: cifra e indexa de verdad, sin secretos en el código. */
    @Spy private ProteccionDatosIdentidadService proteccion = new ProteccionDatosIdentidadService(
            new CifradorAesGcmDatosIdentidad(java.util.Base64.getEncoder().encodeToString(
                    new java.security.SecureRandom().generateSeed(32)), true));

    @InjectMocks
    private ClienteService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "verificationUrl", "https://app.test/verificar");
    }

    @Nested
    @DisplayName("Registrar cliente")
    class RegistrarCliente {

        @Test
        @DisplayName("Lanza excepción si las contraseñas no coinciden")
        void registrar_passwordsDistintas_lanzaExcepcion() {
            RegistroClienteRequestDTO dto = new RegistroClienteRequestDTO(
                    "Cliente", "c@test.com", "Pass123!", "OtherPass!", "123", "Calle 1", "Bogota", true, "1.0", true, "1.0");

            assertThatThrownBy(() -> service.registrarCliente(dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Las contraseñas no coinciden");
        }

        @Test
        @DisplayName("Lanza excepción si el correo ya se encuentra registrado")
        void registrar_correoDuplicado_lanzaExcepcion() {
            RegistroClienteRequestDTO dto = new RegistroClienteRequestDTO(
                    "Cliente", "c@test.com", "Pass123!", "Pass123!", "123", "Calle 1", "Bogota", true, "1.0", true, "1.0");
            when(usuarioRepository.existsByEmail("c@test.com")).thenReturn(true);

            assertThatThrownBy(() -> service.registrarCliente(dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("El correo ya se encuentra registrado");
        }

        private RegistroClienteRequestDTO registro(String email, String cedula, String fecha) {
            return new RegistroClienteRequestDTO("Carlos Gomez", email, "ClaveSegura1!", "ClaveSegura1!",
                    "3001234567", "Carrera 7 # 45-10", "Medellin", true, "1.0", true, "1.0", cedula, fecha);
        }

        private Usuario guardadoCon(Long id, String email) {
            return Usuario.builder().id(id).nombre("Carlos Gomez").email(email)
                    .rol(Rol.CLIENTE).estado(EstadoUsuario.PENDIENTE_VERIFICACION).build();
        }

        @Test
        @DisplayName("Registra al cliente con su identidad cifrada y bloqueada, y despacha el correo de verificación")
        void registrar_exitoso_creaClienteConIdentidad() {
            RegistroClienteRequestDTO dto = registro("carlos@test.com", "1.000.000.001", "1990-05-17");
            when(passwordEncoder.encode("ClaveSegura1!")).thenReturn("hashedPass");
            when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenReturn(guardadoCon(101L, "carlos@test.com"));

            RegistroClienteResponseDTO res = service.registrarCliente(dto);

            assertThat(res).isEqualTo(RegistroClienteResponseDTO.NEUTRA);
            ArgumentCaptor<Usuario> creado = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).saveAndFlush(creado.capture());
            assertThat(creado.getValue().isDatosIdentidadBloqueados()).isTrue();
            assertThat(creado.getValue().getNumeroCedulaHash()).isEqualTo(proteccion.indiceCiego("1000000001"));
            assertThat(creado.getValue().getNumeroCedulaCifrado()).doesNotContain("1000000001");
            assertThat(creado.getValue().getFechaNacimientoCifrada()).doesNotContain("1990");
            verify(tokenRepository).save(any(TokenVerificacion.class));
            verify(emailService).enviarVerificacion(eq("carlos@test.com"), eq("Carlos Gomez"), anyString());
            verifyNoInteractions(avisoIntentoRegistro);
        }

        @Test
        @DisplayName("Anti-enumeración: con la cédula de otra cuenta, la respuesta es idéntica, no se crea nada y se avisa al titular")
        void registrar_cedulaDeOtraCuenta_respuestaNeutraSinCrear() {
            Usuario titular = guardadoCon(7L, "titular@test.com");
            when(usuarioRepository.findByNumeroCedulaHash(proteccion.indiceCiego("1000000001")))
                    .thenReturn(Optional.of(titular));

            RegistroClienteResponseDTO res = service.registrarCliente(registro("otro@test.com", "1000000001", "1990-05-17"));

            assertThat(res).isEqualTo(RegistroClienteResponseDTO.NEUTRA);
            assertThat(res.toString()).doesNotContain("1000000001").doesNotContain("titular");
            verify(avisoIntentoRegistro).avisarTitular(titular);
            verify(usuarioRepository, never()).saveAndFlush(any());
            verifyNoInteractions(clienteRepository, tokenRepository);
            verify(emailService, never()).enviarVerificacion(anyString(), anyString(), anyString());
            // Mismo trabajo costoso (BCrypt) que un registro nuevo, para no delatar la existencia por el tiempo.
            verify(passwordEncoder).encode("ClaveSegura1!");
        }

        @Test
        @DisplayName("Anti-enumeración en carrera: si otro registro gana la constraint única, la respuesta sigue siendo neutra")
        void registrar_carreraPorCedula_respuestaNeutra() {
            Usuario ganador = guardadoCon(8L, "ganador@test.com");
            when(usuarioRepository.findByNumeroCedulaHash(anyString()))
                    .thenReturn(Optional.empty(), Optional.of(ganador));
            when(usuarioRepository.saveAndFlush(any(Usuario.class)))
                    .thenThrow(new DataIntegrityViolationException("uk_usuarios_numero_cedula_hash"));

            RegistroClienteResponseDTO res = service.registrarCliente(registro("carrera@test.com", "1000000001", "1990-05-17"));

            assertThat(res).isEqualTo(RegistroClienteResponseDTO.NEUTRA);
            verify(avisoIntentoRegistro).avisarTitular(ganador);
            verify(emailService, never()).enviarVerificacion(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("Una violación de integridad ajena a la cédula (correo en carrera) no se oculta")
        void registrar_carreraPorCorreo_propagaError() {
            when(usuarioRepository.saveAndFlush(any(Usuario.class)))
                    .thenThrow(new DataIntegrityViolationException("usuarios_email_key"));

            assertThatThrownBy(() -> service.registrarCliente(registro("carrera@test.com", "1000000001", "1990-05-17")))
                    .isInstanceOf(DataIntegrityViolationException.class);
            verifyNoInteractions(avisoIntentoRegistro);
        }

        @ParameterizedTest
        @ValueSource(strings = {"12345", "0123456789", "12345678901", "1234567' OR '1'='1", "<script>alert(1)</script>",
                "\u0661\u0662\u0663\u0664\u0665\u0666\u0667", "123-456-789"})
        @DisplayName("Negativo CA-1: rechaza cédulas inválidas o maliciosas por campo, sin repetirlas y sin escribir nada")
        void registrar_cedulaInvalida_rechazaPorCampo(String cedula) {
            assertThatThrownBy(() -> service.registrarCliente(registro("c@test.com", cedula, "1990-05-17")))
                    .isInstanceOf(DatoIdentidadInvalidoException.class)
                    .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(cedula))
                    .extracting("campo").isEqualTo("numeroCedula");
            verify(usuarioRepository, never()).saveAndFlush(any());
            verify(passwordEncoder, never()).encode(anyString());
        }

        @ParameterizedTest
        @ValueSource(strings = {"2023-02-30", "2023-02-29", "2024-13-01", "2024-00-10", "1899-12-31", "0000-01-01",
                "17-05-1990", "1990/05/17", "+1990-05-1", "1990-05-17T00:00", "hoy"})
        @DisplayName("Negativo CA-1: rechaza fechas irreales, mal formadas o fuera de rango con un mensaje del campo")
        void registrar_fechaInvalida_rechazaPorCampo(String fecha) {
            assertThatThrownBy(() -> service.registrarCliente(registro("c@test.com", "1000000001", fecha)))
                    .isInstanceOf(DatoIdentidadInvalidoException.class)
                    .extracting("campo").isEqualTo("fechaNacimiento");
            verify(usuarioRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Negativo CA-1: rechaza una fecha de nacimiento futura (en la zona operativa)")
        void registrar_fechaFutura_rechaza() {
            String manana = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(1).toString();
            assertThatThrownBy(() -> service.registrarCliente(registro("c@test.com", "1000000001", manana)))
                    .isInstanceOf(DatoIdentidadInvalidoException.class)
                    .hasMessage("La fecha de nacimiento no puede ser futura");
        }

        @Test
        @DisplayName("Negativo CA-1: la cédula y la fecha son obligatorias aunque se use la firma anterior a HU-13")
        void registrar_sinIdentidad_rechaza() {
            RegistroClienteRequestDTO anterior = new RegistroClienteRequestDTO(
                    "Carlos Gomez", "carlos@test.com", "ClaveSegura1!", "ClaveSegura1!",
                    "3001234567", "Carrera 7 # 45-10", "Medellin", true, "1.0", true, "1.0");
            assertThatThrownBy(() -> service.registrarCliente(anterior))
                    .isInstanceOf(DatoIdentidadInvalidoException.class)
                    .extracting("campo").isEqualTo("numeroCedula");
        }
    }

    @Nested
    @DisplayName("Reenviar verificación")
    class ReenviarVerificacion {

        @Test
        @DisplayName("Genera nuevo token si el usuario está pendiente y no se ha enviado en el último minuto")
        void reenviarVerificacion_exitoso() {
            Usuario usuario = Usuario.builder()
                    .id(50L).email("c@test.com").nombre("Carlos")
                    .rol(Rol.CLIENTE).activo(true).estado(EstadoUsuario.PENDIENTE_VERIFICACION).build();
            when(usuarioRepository.findByEmail("c@test.com")).thenReturn(Optional.of(usuario));
            when(tokenRepository.findByUsuarioId(50L)).thenReturn(Optional.empty());

            service.reenviarVerificacion("c@test.com");

            verify(tokenRepository).save(any(TokenVerificacion.class));
            verify(emailService).enviarVerificacion(eq("c@test.com"), eq("Carlos"), anyString());
        }

        @Test
        @DisplayName("Omite reenvío si se envió hace menos de 1 minuto")
        void reenviarVerificacion_reciente_noHaceNada() {
            Usuario usuario = Usuario.builder()
                    .id(50L).email("c@test.com").nombre("Carlos")
                    .rol(Rol.CLIENTE).activo(true).estado(EstadoUsuario.PENDIENTE_VERIFICACION).build();
            TokenVerificacion tokenPrevio = TokenVerificacion.builder()
                    .usuario(usuario)
                    .fechaExpiracion(LocalDateTime.now().plusHours(2)) // Creado hace instantes
                    .build();

            when(usuarioRepository.findByEmail("c@test.com")).thenReturn(Optional.of(usuario));
            when(tokenRepository.findByUsuarioId(50L)).thenReturn(Optional.of(tokenPrevio));

            service.reenviarVerificacion("c@test.com");

            verify(emailService, never()).enviarVerificacion(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("Verificar cuenta")
    class VerificarCuenta {

        @Test
        @DisplayName("Lanza excepción si el token no existe")
        void verificarCuenta_tokenInexistente_lanzaExcepcion() {
            when(tokenRepository.findByToken(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.verificarCuenta("tokenInvalido"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Token de verificación inválido");
        }

        @Test
        @DisplayName("Lanza excepción si el token está expirado")
        void verificarCuenta_tokenExpirado_lanzaExcepcion() {
            TokenVerificacion tv = TokenVerificacion.builder()
                    .fechaExpiracion(LocalDateTime.now().minusHours(1))
                    .build();
            when(tokenRepository.findByToken(anyString())).thenReturn(Optional.of(tv));

            assertThatThrownBy(() -> service.verificarCuenta("tokenExp"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ha expirado");
        }

        @Test
        @DisplayName("Lanza excepción si la cuenta ya no está pendiente de verificación")
        void verificarCuenta_estadoNoPendiente_lanzaExcepcion() {
            Usuario usuario = Usuario.builder()
                    .id(1L).rol(Rol.CLIENTE).activo(true).estado(EstadoUsuario.ACTIVO).build();
            TokenVerificacion tv = TokenVerificacion.builder()
                    .usuario(usuario)
                    .fechaExpiracion(LocalDateTime.now().plusHours(1))
                    .build();
            when(tokenRepository.findByToken(anyString())).thenReturn(Optional.of(tv));

            assertThatThrownBy(() -> service.verificarCuenta("tokenValido"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ya no corresponde");
        }

        @Test
        @DisplayName("Activa la cuenta y elimina el token de verificación")
        void verificarCuenta_exitoso_activaUsuario() {
            Usuario usuario = Usuario.builder()
                    .id(1L).rol(Rol.CLIENTE).activo(true).estado(EstadoUsuario.PENDIENTE_VERIFICACION).build();
            TokenVerificacion tv = TokenVerificacion.builder()
                    .usuario(usuario)
                    .fechaExpiracion(LocalDateTime.now().plusHours(1))
                    .build();
            when(tokenRepository.findByToken(anyString())).thenReturn(Optional.of(tv));

            service.verificarCuenta("tokenValido");

            assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
            verify(usuarioRepository).save(usuario);
            verify(tokenRepository).delete(tv);
        }
    }

    @Nested
    @DisplayName("Consultar y actualizar perfil")
    class PerfilCliente {

        @Test
        @DisplayName("Obtiene el perfil del cliente autenticado")
        void obtenerPerfilActual_exitoso() {
            Usuario u = Usuario.builder().id(10L).nombre("Ana").email("ana@test.com")
                    .telefono("311").direccion("Calle 5").rol(Rol.CLIENTE).estado(EstadoUsuario.ACTIVO).build();
            Cliente c = Cliente.builder().id(20L).usuario(u).ciudad("Cali").build();
            when(actorAuthorizationService.clienteActualId()).thenReturn(20L);
            when(clienteRepository.findById(20L)).thenReturn(Optional.of(c));

            ClienteResponseDTO res = service.obtenerPerfilActual();

            assertThat(res.idCliente()).isEqualTo(20L);
            assertThat(res.nombre()).isEqualTo("Ana");
            assertThat(res.ciudad()).isEqualTo("Cali");
        }

        @Test
        @DisplayName("Actualiza el perfil del cliente autenticado")
        void actualizarPerfilActual_exitoso() {
            Usuario u = Usuario.builder().id(10L).nombre("Viejo").email("ana@test.com").rol(Rol.CLIENTE).build();
            when(actorAuthorizationService.actorActual()).thenReturn(u);
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

            ActualizarPerfilRequestDTO dto = new ActualizarPerfilRequestDTO("Nuevo", "322", "Nueva Dir");
            UsuarioResponseDTO res = service.actualizarPerfilActual(dto);

            assertThat(res.nombre()).isEqualTo("Nuevo");
            assertThat(res.telefono()).isEqualTo("322");
            assertThat(res.direccion()).isEqualTo("Nueva Dir");
        }

        @Test
        @DisplayName("Desactiva la cuenta del cliente autenticado")
        void desactivarCuentaActual_exitoso() {
            Usuario u = Usuario.builder().id(10L).rol(Rol.CLIENTE).activo(true).estado(EstadoUsuario.ACTIVO).build();
            when(actorAuthorizationService.actorActual()).thenReturn(u);

            service.desactivarCuentaActual();

            assertThat(u.getActivo()).isFalse();
            assertThat(u.getEstado()).isEqualTo(EstadoUsuario.INACTIVO);
            verify(usuarioRepository).save(u);
        }

        @Test
        @DisplayName("Lanza excepción si intenta desactivar cuenta y el rol no es cliente")
        void desactivarCuentaActual_noCliente_lanzaExcepcion() {
            Usuario u = Usuario.builder().id(10L).rol(Rol.OPERADOR).build();
            when(actorAuthorizationService.actorActual()).thenReturn(u);

            assertThatThrownBy(() -> service.desactivarCuentaActual())
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Actualiza perfil por id verificando propietario")
        void actualizarPerfil_porId_exitoso() {
            Usuario u = Usuario.builder().id(15L).email("c@test.com").nombre("Ana").rol(Rol.CLIENTE).build();
            when(usuarioRepository.findById(15L)).thenReturn(Optional.of(u));
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

            ActualizarPerfilRequestDTO dto = new ActualizarPerfilRequestDTO("Ana Maria", "333", "Dir");
            UsuarioResponseDTO res = service.actualizarPerfil(15L, dto, "c@test.com");

            assertThat(res.nombre()).isEqualTo("Ana Maria");
            verify(actorAuthorizationService).exigirPropietario(15L, Rol.CLIENTE);
        }

        @Test
        @DisplayName("Lanza AccessDeniedException si el email autenticado no coincide con el usuario a editar")
        void actualizarPerfil_noPropietario_lanzaAccessDenied() {
            Usuario u = Usuario.builder().id(15L).email("c@test.com").build();
            when(usuarioRepository.findById(15L)).thenReturn(Optional.of(u));

            ActualizarPerfilRequestDTO dto = new ActualizarPerfilRequestDTO("Ana", "333", "Dir");
            assertThatThrownBy(() -> service.actualizarPerfil(15L, dto, "hacker@test.com"))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("Desactiva cuenta cliente por id verificando propietario")
        void desactivarCuentaCliente_porId_exitoso() {
            Usuario u = Usuario.builder().id(15L).email("c@test.com").rol(Rol.CLIENTE).activo(true).build();
            when(usuarioRepository.findById(15L)).thenReturn(Optional.of(u));

            service.desactivarCuentaCliente(15L, "c@test.com");

            assertThat(u.getActivo()).isFalse();
            assertThat(u.getEstado()).isEqualTo(EstadoUsuario.INACTIVO);
            verify(actorAuthorizationService).exigirPropietario(15L, Rol.CLIENTE);
            verify(usuarioRepository).save(u);
        }
    }
}

package com.udea.demo.usuarios.application.service;

import com.udea.demo.usuarios.application.dto.CrearUsuarioInternoCommand;
import com.udea.demo.usuarios.application.dto.EditarUsuarioInternoCommand;
import com.udea.demo.usuarios.application.dto.ResultadoCreacionUsuarioInterno;
import com.udea.demo.usuarios.application.dto.UsuarioResponseDTO;
import com.udea.demo.usuarios.domain.exception.*;
import com.udea.demo.usuarios.domain.model.*;
import com.udea.demo.usuarios.interfaces.persistence.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.ArgumentCaptor;
import com.udea.demo.usuarios.infrastructure.cifrado.CifradorAesGcmDatosIdentidad;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UsuarioInternoService - gestión de usuarios internos (operadores y conductores)")
class UsuarioInternoServiceTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ClienteRepository clienteRepository;
    @Mock private ConductorRepository conductorRepository;
    @Mock private OperadorRepository operadorRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordTemporalService passwordTemporalService;
    @Mock private ActorAuthorizationService actorAuthorizationService;
    @Mock private SesionUsuarioRepository sesionRepository;
    @Mock private PasswordPolicyService passwordPolicyService;
    /** Servicio real con una clave aleatoria por ejecución (sin secretos en el código). */
    @Spy private ProteccionDatosIdentidadService proteccion = new ProteccionDatosIdentidadService(
            new CifradorAesGcmDatosIdentidad(java.util.Base64.getEncoder().encodeToString(
                    new java.security.SecureRandom().generateSeed(32)), true));

    @InjectMocks
    private UsuarioInternoService service;

    @Nested
    @DisplayName("Crear usuario interno")
    class CrearUsuarioInterno {

        @Test
        @DisplayName("Lanza excepción si el rol no es OPERADOR ni CONDUCTOR")
        void crear_rolInvalido_lanzaExcepcion() {
            CrearUsuarioInternoCommand cmd = new CrearUsuarioInternoCommand(
                    "Cliente", "c@test.com", "123456", Rol.CLIENTE, "Dir", null, null);

            assertThatThrownBy(() -> service.crear(cmd))
                    .isInstanceOf(RolInternoInvalidoException.class);
        }

        @Test
        @DisplayName("Lanza excepción si es conductor y la licencia está vacía")
        void crear_conductorSinLicencia_lanzaExcepcion() {
            CrearUsuarioInternoCommand cmd = new CrearUsuarioInternoCommand(
                    "Conductor", "c@test.com", "123456", Rol.CONDUCTOR, "Dir", "   ", null);

            assertThatThrownBy(() -> service.crear(cmd))
                    .isInstanceOf(LicenciaRequeridaException.class);
        }

        @Test
        @DisplayName("Lanza excepción si es operador y el código de empleado está vacío")
        void crear_operadorSinCodigo_lanzaExcepcion() {
            CrearUsuarioInternoCommand cmd = new CrearUsuarioInternoCommand(
                    "Operador", "o@test.com", "123456", Rol.OPERADOR, "Dir", null, "");

            assertThatThrownBy(() -> service.crear(cmd))
                    .isInstanceOf(CodigoEmpleadoRequeridoException.class);
        }

        @Test
        @DisplayName("Lanza excepción si el correo ya se encuentra registrado")
        void crear_correoDuplicado_lanzaExcepcion() {
            CrearUsuarioInternoCommand cmd = new CrearUsuarioInternoCommand(
                    "Operador", "o@test.com", "123456", Rol.OPERADOR, "Dir", null, "EMP-01");
            when(usuarioRepository.existsByEmail("o@test.com")).thenReturn(true);

            assertThatThrownBy(() -> service.crear(cmd))
                    .isInstanceOf(EmailYaRegistradoException.class);
        }

        @Test
        @DisplayName("Lanza excepción si el código de empleado ya está en uso")
        void crear_codigoEmpleadoDuplicado_lanzaExcepcion() {
            CrearUsuarioInternoCommand cmd = new CrearUsuarioInternoCommand(
                    "Operador", "o@test.com", "123456", Rol.OPERADOR, "Dir", null, "EMP-01");
            when(usuarioRepository.existsByEmail("o@test.com")).thenReturn(false);
            when(operadorRepository.existsByCodigoEmpleado("EMP-01")).thenReturn(true);

            assertThatThrownBy(() -> service.crear(cmd))
                    .isInstanceOf(CodigoEmpleadoRequeridoException.class);
        }

        @Test
        @DisplayName("Crea exitosamente un operador con contraseña temporal")
        void crear_operadorExitoso_retornaResultado() {
            CrearUsuarioInternoCommand cmd = new CrearUsuarioInternoCommand(
                    "Operador Uno", "op1@test.com", "3001112233", Rol.OPERADOR, "Calle 1", null, "OP-001", "1000000001", "1990-05-17");
            when(usuarioRepository.existsByEmail("op1@test.com")).thenReturn(false);
            when(operadorRepository.existsByCodigoEmpleado("OP-001")).thenReturn(false);
            when(passwordTemporalService.generar()).thenReturn("TempPass123!");
            when(passwordEncoder.encode("TempPass123!")).thenReturn("encodedHash");

            Usuario guardado = Usuario.builder()
                    .id(10L)
                    .nombre(cmd.nombre())
                    .email(cmd.email())
                    .rol(Rol.OPERADOR)
                    .estado(EstadoUsuario.PENDIENTE_ACTIVACION)
                    .build();
            when(usuarioRepository.save(any(Usuario.class))).thenReturn(guardado);

            ResultadoCreacionUsuarioInterno res = service.crear(cmd);

            assertThat(res.id()).isEqualTo(10L);
            assertThat(res.email()).isEqualTo("op1@test.com");
            assertThat(res.rol()).isEqualTo(Rol.OPERADOR);
            assertThat(res.passwordTemporal()).isEqualTo("TempPass123!");
            verify(operadorRepository).save(any(Operador.class));
        }

        @Test
        @DisplayName("Crea exitosamente un conductor con contraseña temporal")
        void crear_conductorExitoso_retornaResultado() {
            CrearUsuarioInternoCommand cmd = new CrearUsuarioInternoCommand(
                    "Conductor Uno", "cond1@test.com", "3004445566", Rol.CONDUCTOR, "Calle 2", "LIC-999", null, "1000000002", "1985-11-30");
            when(usuarioRepository.existsByEmail("cond1@test.com")).thenReturn(false);
            when(passwordTemporalService.generar()).thenReturn("TempPass456!");
            when(passwordEncoder.encode("TempPass456!")).thenReturn("encodedHash2");

            Usuario guardado = Usuario.builder()
                    .id(20L)
                    .nombre(cmd.nombre())
                    .email(cmd.email())
                    .rol(Rol.CONDUCTOR)
                    .estado(EstadoUsuario.PENDIENTE_ACTIVACION)
                    .build();
            when(usuarioRepository.save(any(Usuario.class))).thenReturn(guardado);

            ResultadoCreacionUsuarioInterno res = service.crear(cmd);

            assertThat(res.id()).isEqualTo(20L);
            assertThat(res.email()).isEqualTo("cond1@test.com");
            assertThat(res.rol()).isEqualTo(Rol.CONDUCTOR);
            verify(conductorRepository).save(any(Conductor.class));
        }

        @Test
        @DisplayName("HU-13: el usuario interno se crea con su identidad cifrada y bloqueada")
        void crear_conIdentidad_quedaBloqueada() {
            CrearUsuarioInternoCommand cmd = new CrearUsuarioInternoCommand("Conductor Dos", "cond2@test.com",
                    "3004445566", Rol.CONDUCTOR, "Calle 3", "LIC-1000", null, "1.000.000.003", "1980-01-15");
            when(passwordTemporalService.generar()).thenReturn("TempPass789!");
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

            service.crear(cmd);

            ArgumentCaptor<Usuario> creado = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(creado.capture());
            assertThat(creado.getValue().isDatosIdentidadBloqueados()).isTrue();
            assertThat(creado.getValue().getNumeroCedulaHash()).isEqualTo(proteccion.indiceCiego("1000000003"));
            assertThat(creado.getValue().getNumeroCedulaCifrado()).doesNotContain("1000000003");
        }

        @Test
        @DisplayName("Negativo anti-enumeración: con la cédula de otra cuenta, el mensaje es genérico y no se crea nada")
        void crear_cedulaDeOtraCuenta_mensajeGenerico() {
            CrearUsuarioInternoCommand cmd = new CrearUsuarioInternoCommand("Operador Dos", "op2@test.com",
                    "3001112233", Rol.OPERADOR, "Calle 1", null, "OP-002", "1000000004", "1990-05-17");
            when(usuarioRepository.existsByNumeroCedulaHash(proteccion.indiceCiego("1000000004"))).thenReturn(true);

            assertThatThrownBy(() -> service.crear(cmd))
                    .isInstanceOf(IdentidadNoRegistrableException.class)
                    .hasMessage(IdentidadNoRegistrableException.MENSAJE)
                    .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("1000000004")
                            .doesNotContainIgnoringCase("registrad").doesNotContainIgnoringCase("existe"));
            verify(usuarioRepository, never()).save(any());
            verifyNoInteractions(passwordTemporalService);
        }

        @ParameterizedTest
        @ValueSource(strings = {"2023-02-30", "2999-01-01", "1899-12-31", "1990/05/17"})
        @DisplayName("Negativo CA-1: rechaza fechas irreales, futuras o mal formadas con un mensaje del campo")
        void crear_fechaInvalida_rechaza(String fecha) {
            CrearUsuarioInternoCommand cmd = new CrearUsuarioInternoCommand("Operador Tres", "op3@test.com",
                    "3001112233", Rol.OPERADOR, "Calle 1", null, "OP-003", "1000000005", fecha);
            assertThatThrownBy(() -> service.crear(cmd))
                    .isInstanceOf(DatoIdentidadInvalidoException.class)
                    .extracting("campo").isEqualTo("fechaNacimiento");
            verify(usuarioRepository, never()).save(any());
        }

        @Test
        @DisplayName("Negativo CA-1: sin cédula (firma anterior a HU-13) no se crea el usuario")
        void crear_sinCedula_rechaza() {
            CrearUsuarioInternoCommand cmd = new CrearUsuarioInternoCommand(
                    "Operador Cuatro", "op4@test.com", "3001112233", Rol.OPERADOR, "Calle 1", null, "OP-004");
            assertThatThrownBy(() -> service.crear(cmd))
                    .isInstanceOf(DatoIdentidadInvalidoException.class)
                    .extracting("campo").isEqualTo("numeroCedula");
            verify(usuarioRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Editar usuario interno")
    class EditarUsuarioInterno {

        @Test
        @DisplayName("Lanza excepción si el usuario no existe")
        void editar_noExiste_lanzaExcepcion() {
            EditarUsuarioInternoCommand cmd = new EditarUsuarioInternoCommand("Nombre", null, null, null, null, null);
            when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.editar(99L, cmd))
                    .isInstanceOf(UsuarioNoEncontradoException.class);
        }

        @Test
        @DisplayName("Actualiza datos básicos manteniendo el mismo rol")
        void editar_datosBasicos_actualizaUsuario() {
            Usuario usuario = Usuario.builder()
                    .id(1L).nombre("Viejo").telefono("111").direccion("DirVieja")
                    .rol(Rol.OPERADOR).estado(EstadoUsuario.ACTIVO).build();
            when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
            when(operadorRepository.findByUsuarioId(1L)).thenReturn(Optional.of(Operador.builder().usuario(usuario).codigoEmpleado("E-1").build()));
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

            EditarUsuarioInternoCommand cmd = new EditarUsuarioInternoCommand("Nuevo", "222", "DirNueva", null, null, null);
            UsuarioResponseDTO res = service.editar(1L, cmd);

            assertThat(res.nombre()).isEqualTo("Nuevo");
            assertThat(res.telefono()).isEqualTo("222");
            assertThat(res.direccion()).isEqualTo("DirNueva");
            verify(sesionRepository, never()).deleteByUsuarioId(anyLong());
        }

        @Test
        @DisplayName("Actualiza a conductor existente con nueva licencia")
        void editar_conductorExistente_actualizaLicencia() {
            Usuario usuario = Usuario.builder().id(2L).rol(Rol.CONDUCTOR).estado(EstadoUsuario.ACTIVO).build();
            Conductor conductor = Conductor.builder().usuario(usuario).licencia("LIC-OLD").build();
            when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario));
            when(conductorRepository.findByUsuarioId(2L)).thenReturn(Optional.of(conductor));
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

            EditarUsuarioInternoCommand cmd = new EditarUsuarioInternoCommand(null, null, null, Rol.CONDUCTOR, "LIC-NEW", null);
            service.editar(2L, cmd);

            assertThat(conductor.getLicencia()).isEqualTo("LIC-NEW");
            verify(conductorRepository).save(conductor);
        }

        @Test
        @DisplayName("Lanza excepción si al editar conductor se proporciona licencia vacía")
        void editar_conductorLicenciaVacia_lanzaExcepcion() {
            Usuario usuario = Usuario.builder().id(2L).rol(Rol.CONDUCTOR).estado(EstadoUsuario.ACTIVO).build();
            when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario));
            when(conductorRepository.findByUsuarioId(2L)).thenReturn(Optional.of(Conductor.builder().usuario(usuario).build()));

            EditarUsuarioInternoCommand cmd = new EditarUsuarioInternoCommand(null, null, null, Rol.CONDUCTOR, "  ", null);
            assertThatThrownBy(() -> service.editar(2L, cmd))
                    .isInstanceOf(LicenciaRequeridaException.class);
        }

        @Test
        @DisplayName("Crea registro conductor si el usuario no tenía fila de conductor previa")
        void editar_conductorNuevo_creaFilaConductor() {
            Usuario usuario = Usuario.builder().id(3L).rol(Rol.OPERADOR).estado(EstadoUsuario.PENDIENTE_VERIFICACION).build();
            when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuario));
            when(conductorRepository.findByUsuarioId(3L)).thenReturn(Optional.empty());
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

            EditarUsuarioInternoCommand cmd = new EditarUsuarioInternoCommand(null, null, null, Rol.CONDUCTOR, "LIC-123", null);
            UsuarioResponseDTO res = service.editar(3L, cmd);

            assertThat(res.rol()).isEqualTo(Rol.CONDUCTOR);
            assertThat(res.estado()).isEqualTo(EstadoUsuario.ACTIVO);
            verify(conductorRepository).save(any(Conductor.class));
            verify(sesionRepository).deleteByUsuarioId(3L);
        }

        @Test
        @DisplayName("Lanza excepción si al convertir a conductor no se pasa licencia")
        void editar_conductorNuevoSinLicencia_lanzaExcepcion() {
            Usuario usuario = Usuario.builder().id(3L).rol(Rol.OPERADOR).estado(EstadoUsuario.ACTIVO).build();
            when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuario));
            when(conductorRepository.findByUsuarioId(3L)).thenReturn(Optional.empty());

            EditarUsuarioInternoCommand cmd = new EditarUsuarioInternoCommand(null, null, null, Rol.CONDUCTOR, "", null);
            assertThatThrownBy(() -> service.editar(3L, cmd))
                    .isInstanceOf(LicenciaRequeridaException.class);
        }

        @Test
        @DisplayName("Actualiza a operador existente con nuevo código no repetido")
        void editar_operadorExistente_actualizaCodigo() {
            Usuario usuario = Usuario.builder().id(4L).rol(Rol.OPERADOR).estado(EstadoUsuario.ACTIVO).build();
            Operador operador = Operador.builder().usuario(usuario).codigoEmpleado("OLD-COD").build();
            when(usuarioRepository.findById(4L)).thenReturn(Optional.of(usuario));
            when(operadorRepository.findByUsuarioId(4L)).thenReturn(Optional.of(operador));
            when(operadorRepository.existsByCodigoEmpleado("NEW-COD")).thenReturn(false);
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

            EditarUsuarioInternoCommand cmd = new EditarUsuarioInternoCommand(null, null, null, Rol.OPERADOR, null, "NEW-COD");
            service.editar(4L, cmd);

            assertThat(operador.getCodigoEmpleado()).isEqualTo("NEW-COD");
            verify(operadorRepository).save(operador);
        }

        @Test
        @DisplayName("Lanza excepción si el nuevo código de operador ya está registrado por otro")
        void editar_operadorCodigoDuplicado_lanzaExcepcion() {
            Usuario usuario = Usuario.builder().id(4L).rol(Rol.OPERADOR).estado(EstadoUsuario.ACTIVO).build();
            Operador operador = Operador.builder().usuario(usuario).codigoEmpleado("OLD-COD").build();
            when(usuarioRepository.findById(4L)).thenReturn(Optional.of(usuario));
            when(operadorRepository.findByUsuarioId(4L)).thenReturn(Optional.of(operador));
            when(operadorRepository.existsByCodigoEmpleado("EXISTING-COD")).thenReturn(true);

            EditarUsuarioInternoCommand cmd = new EditarUsuarioInternoCommand(null, null, null, Rol.OPERADOR, null, "EXISTING-COD");
            assertThatThrownBy(() -> service.editar(4L, cmd))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Crea registro operador si el usuario pasa a operador")
        void editar_operadorNuevo_creaFilaOperador() {
            Usuario usuario = Usuario.builder().id(5L).rol(Rol.CONDUCTOR).estado(EstadoUsuario.ACTIVO).build();
            when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));
            when(operadorRepository.findByUsuarioId(5L)).thenReturn(Optional.empty());
            when(operadorRepository.existsByCodigoEmpleado("OP-99")).thenReturn(false);
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

            EditarUsuarioInternoCommand cmd = new EditarUsuarioInternoCommand(null, null, null, Rol.OPERADOR, null, "OP-99");
            UsuarioResponseDTO res = service.editar(5L, cmd);

            assertThat(res.rol()).isEqualTo(Rol.OPERADOR);
            verify(operadorRepository).save(any(Operador.class));
            verify(sesionRepository).deleteByUsuarioId(5L);
        }

        @Test
        @DisplayName("Convierte usuario a CLIENTE creando fila de cliente si no existía")
        void editar_destinoCliente_creaFilaCliente() {
            Usuario usuario = Usuario.builder().id(6L).rol(Rol.OPERADOR).estado(EstadoUsuario.ACTIVO).build();
            when(usuarioRepository.findById(6L)).thenReturn(Optional.of(usuario));
            when(clienteRepository.findByUsuarioId(6L)).thenReturn(Optional.empty());
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

            EditarUsuarioInternoCommand cmd = new EditarUsuarioInternoCommand(null, null, null, Rol.CLIENTE, null, null);
            UsuarioResponseDTO res = service.editar(6L, cmd);

            assertThat(res.rol()).isEqualTo(Rol.CLIENTE);
            verify(clienteRepository).save(any(Cliente.class));
            verify(sesionRepository).deleteByUsuarioId(6L);
        }
    }

    @Nested
    @DisplayName("Desactivar usuario interno")
    class DesactivarUsuarioInterno {

        @Test
        @DisplayName("Lanza excepción si el usuario no existe")
        void desactivar_noExiste_lanzaExcepcion() {
            when(usuarioRepository.findById(88L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.desactivar(88L))
                    .isInstanceOf(UsuarioNoEncontradoException.class);
        }

        @Test
        @DisplayName("Lanza excepción si el rol no es OPERADOR ni CONDUCTOR")
        void desactivar_rolInvalido_lanzaExcepcion() {
            Usuario usuario = Usuario.builder().id(1L).rol(Rol.CLIENTE).activo(true).build();
            when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

            assertThatThrownBy(() -> service.desactivar(1L))
                    .isInstanceOf(RolInternoInvalidoException.class);
        }

        @Test
        @DisplayName("Desactiva exitosamente un operador y revoca sesiones")
        void desactivar_operador_exitoso() {
            Usuario usuario = Usuario.builder().id(2L).rol(Rol.OPERADOR).activo(true).estado(EstadoUsuario.ACTIVO).build();
            when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario));

            service.desactivar(2L);

            assertThat(usuario.getActivo()).isFalse();
            assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.INACTIVO);
            verify(usuarioRepository).save(usuario);
            verify(sesionRepository).deleteByUsuarioId(2L);
        }
    }

    @Nested
    @DisplayName("Cambiar contraseña")
    class CambiarPassword {

        @Test
        @DisplayName("Lanza excepción si el email autenticado no coincide")
        void cambiarPassword_emailNoCoincide_lanzaAccessDenied() {
            Usuario usuario = Usuario.builder().id(1L).email("user@test.com").rol(Rol.OPERADOR).build();
            when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

            assertThatThrownBy(() -> service.cambiarPassword(1L, "otro@test.com", "Old123!", "New123!", "New123!"))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("Lanza excepción si la confirmación no coincide")
        void cambiarPassword_passwordNoCoincide_lanzaExcepcion() {
            Usuario usuario = Usuario.builder().id(1L).email("user@test.com").rol(Rol.OPERADOR).build();
            when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

            assertThatThrownBy(() -> service.cambiarPassword(1L, "user@test.com", "Old123!", "New123!", "Different!"))
                    .isInstanceOf(PasswordNoCoincideException.class);
        }

        @Test
        @DisplayName("Lanza excepción si la cuenta está inactiva")
        void cambiarPassword_cuentaInactiva_lanzaExcepcion() {
            Usuario usuario = Usuario.builder().id(1L).email("user@test.com").rol(Rol.OPERADOR).activo(false).build();
            when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));

            assertThatThrownBy(() -> service.cambiarPassword(1L, "user@test.com", "Old123!", "New123!", "New123!"))
                    .isInstanceOf(CuentaInactivaException.class);
        }

        @Test
        @DisplayName("Lanza excepción si la contraseña actual no es correcta")
        void cambiarPassword_actualIncorrecta_lanzaExcepcion() {
            Usuario usuario = Usuario.builder().id(1L).email("user@test.com").password("hashedOld")
                    .rol(Rol.OPERADOR).activo(true).build();
            when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
            when(passwordEncoder.matches("Wrong!", "hashedOld")).thenReturn(false);

            assertThatThrownBy(() -> service.cambiarPassword(1L, "user@test.com", "Wrong!", "NewPass123!", "NewPass123!"))
                    .isInstanceOf(PasswordDebilException.class);
        }

        @Test
        @DisplayName("Lanza excepción si la nueva contraseña es igual a la actual")
        void cambiarPassword_nuevaIgualAAnterior_lanzaExcepcion() {
            Usuario usuario = Usuario.builder().id(1L).email("user@test.com").password("hashedOld")
                    .rol(Rol.OPERADOR).activo(true).build();
            when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
            when(passwordEncoder.matches("Same123!", "hashedOld")).thenReturn(true);

            assertThatThrownBy(() -> service.cambiarPassword(1L, "user@test.com", "Same123!", "Same123!", "Same123!"))
                    .isInstanceOf(PasswordDebilException.class);
        }

        @Test
        @DisplayName("Cambia exitosamente la contraseña y activa usuario si estaba pendiente")
        void cambiarPassword_exitoso_activaUsuario() {
            Usuario usuario = Usuario.builder().id(1L).email("user@test.com").password("hashedOld")
                    .rol(Rol.OPERADOR).activo(true).estado(EstadoUsuario.PENDIENTE_ACTIVACION).build();
            when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
            when(passwordEncoder.matches("Old123!", "hashedOld")).thenReturn(true);
            when(passwordEncoder.matches("NewPass123!", "hashedOld")).thenReturn(false);
            when(passwordEncoder.encode("NewPass123!")).thenReturn("hashedNew");

            service.cambiarPassword(1L, "user@test.com", "Old123!", "NewPass123!", "NewPass123!");

            assertThat(usuario.getPassword()).isEqualTo("hashedNew");
            assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
            verify(usuarioRepository).save(usuario);
            verify(sesionRepository).deleteByUsuarioId(1L);
        }
    }
}

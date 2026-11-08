package com.udea.demo.usuarios.application.service;

import com.udea.demo.usuarios.application.dto.CompletarIdentidadRequestDTO;
import com.udea.demo.usuarios.application.dto.IdentidadPerfilResponseDTO;
import com.udea.demo.usuarios.domain.exception.DatosIdentidadInmutablesException;
import com.udea.demo.usuarios.domain.exception.IdentidadNoRegistrableException;
import com.udea.demo.usuarios.domain.model.DatosIdentidadProtegidos;
import com.udea.demo.usuarios.domain.model.Usuario;
import com.udea.demo.usuarios.interfaces.persistence.UsuarioRepository;
import com.udea.demo.usuarios.interfaces.services.IdentidadPerfilServiceI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HU-13: consulta y completado de la identidad del propio usuario. Es el flujo para los perfiles incompletos
 * marcados por V26.
 *
 * <ul>
 *   <li><b>Anti-IDOR.</b> El usuario sale de la sesión y no hay ningún id en la ruta.</li>
 *   <li><b>CA-3.</b> Solo se completa una vez. Un segundo intento responde 409 IDENTIDAD_INMUTABLE, y el trigger
 *       de V24 lo garantiza también en la BD.</li>
 *   <li><b>CA-2.</b> La edad se calcula aquí, en la zona operativa. No se acepta ninguna edad del cliente.</li>
 *   <li><b>Anti-enumeración.</b> Si la cédula pertenece a otra cuenta, el mensaje es genérico y se avisa al titular.
 *       Para que el endpoint no sirva para probar cédulas una a una, hay un límite de intentos por usuario y hora.</li>
 *   <li><b>Auditoría.</b> Se registra usuario, instante UTC y origen (lista blanca), sin datos personales.</li>
 * </ul>
 */
@Service
public class IdentidadPerfilService implements IdentidadPerfilServiceI {
    private static final Logger log = LoggerFactory.getLogger(IdentidadPerfilService.class);
    static final Set<String> ORIGENES = Set.of("WEB", "MOVIL");
    private static final int MAX_ENTRADAS = 50_000;

    private final ActorAuthorizationService actores;
    private final UsuarioRepository usuarios;
    private final ProteccionDatosIdentidadService proteccion;
    private final AvisoIntentoRegistroService avisos;
    private final int maxIntentosPorHora;
    private final Map<Long, Ventana> intentos = new ConcurrentHashMap<>();

    @PersistenceContext
    private EntityManager entityManager;

    public IdentidadPerfilService(ActorAuthorizationService actores, UsuarioRepository usuarios,
                                  ProteccionDatosIdentidadService proteccion, AvisoIntentoRegistroService avisos,
                                  @Value("${app.identidad.completar-max-intentos-hora:5}") int maxIntentosPorHora) {
        this.actores = actores;
        this.usuarios = usuarios;
        this.proteccion = proteccion;
        this.avisos = avisos;
        this.maxIntentosPorHora = maxIntentosPorHora;
    }

    @Override
    @Transactional(readOnly = true)
    public IdentidadPerfilResponseDTO consultarPropia() {
        return mapear(actores.actorActual());
    }

    @Override
    @Transactional
    public IdentidadPerfilResponseDTO completarPropia(CompletarIdentidadRequestDTO dto, String origen) {
        String origenValido = normalizarOrigen(origen);
        Usuario usuario = actores.actorActual();
        // HU-13 CA-3 / regla 8 (hallazgo de 13-QA-01): bloquear la fila del usuario y recargar su estado ANTES de
        // comprobar. Sin esto, varias peticiones simultáneas veían "sin identidad" y se sobrescribían. El refresh es
        // necesario porque el usuario ya está en el contexto de persistencia (cargado sin bloqueo al autenticar).
        entityManager.refresh(usuario, LockModeType.PESSIMISTIC_WRITE);
        if (usuario.tieneDatosIdentidad()) {
            throw new DatosIdentidadInmutablesException();
        }
        contarIntento(usuario.getId());

        DatosIdentidadProtegidos identidad = proteccion.protegerHoy(dto.numeroCedula(), dto.fechaNacimiento());
        var titular = usuarios.findByNumeroCedulaHash(identidad.numeroCedulaHash());
        if (titular.isPresent()) {
            avisos.avisarTitular(titular.get());
            throw new IdentidadNoRegistrableException();
        }
        Instant ahora = Instant.now();
        usuario.registrarDatosIdentidad(identidad, ahora);
        try {
            usuarios.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException ex) {
            // Carrera: otra cuenta registró la misma cédula, o la misma cuenta se completó en paralelo
            // (trigger V24). En ambos casos, error genérico sin detalles.
            throw new IdentidadNoRegistrableException();
        }
        log.info("Identidad registrada usuarioId={} origen={} fechaUtc={}", usuario.getId(), origenValido,
                ahora.truncatedTo(ChronoUnit.SECONDS));
        return mapear(usuario);
    }

    private IdentidadPerfilResponseDTO mapear(Usuario usuario) {
        if (!usuario.tieneDatosIdentidad()) return IdentidadPerfilResponseDTO.PENDIENTE;
        LocalDate fecha = proteccion.fechaNacimientoDe(usuario);
        return new IdentidadPerfilResponseDTO(true, proteccion.cedulaDe(usuario).enmascarado(), fecha,
                ProteccionDatosIdentidadService.edadEn(fecha, proteccion.hoy()), false);
    }

    static String normalizarOrigen(String origen) {
        if (origen == null || origen.isBlank()) return "WEB";
        String valor = origen.strip().toUpperCase(Locale.ROOT);
        if (!ORIGENES.contains(valor)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        return valor;
    }

    private void contarIntento(Long usuarioId) {
        long hora = Instant.now().getEpochSecond() / 3600;
        if (intentos.size() > MAX_ENTRADAS) intentos.entrySet().removeIf(e -> e.getValue().hora != hora);
        Ventana ventana = intentos.compute(usuarioId, (id, actual) -> {
            if (actual == null || actual.hora != hora) return new Ventana(hora);
            actual.contador.incrementAndGet();
            return actual;
        });
        if (ventana.contador.get() > maxIntentosPorHora) {
            log.warn("Límite de intentos de completar identidad alcanzado usuarioId={}", usuarioId);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    private static final class Ventana {
        private final long hora;
        private final AtomicInteger contador = new AtomicInteger(1);
        private Ventana(long hora) { this.hora = hora; }
    }
}

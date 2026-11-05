package com.udea.demo.pedidos.application.service;

import com.udea.demo.geografia.application.dto.CiudadResponseDTO;
import com.udea.demo.pedidos.application.dto.RegistrarUbicacionRequestDTO;
import com.udea.demo.pedidos.application.dto.UbicacionResponseDTO;
import com.udea.demo.pedidos.domain.exception.CodigoQrInvalidoException;
import com.udea.demo.pedidos.domain.exception.CodigoQrOtroEnvioException;
import com.udea.demo.pedidos.domain.exception.EnvioFinalizadoException;
import com.udea.demo.pedidos.domain.exception.PedidoNoEncontradoException;
import com.udea.demo.pedidos.domain.exception.TransicionEstadoInvalidaException;
import com.udea.demo.pedidos.domain.model.*;
import com.udea.demo.pedidos.interfaces.persistence.EventoUbicacionPedidoRepository;
import com.udea.demo.pedidos.interfaces.persistence.PedidoRepository;
import com.udea.demo.pedidos.application.service.RegistroHistorialPedido;
import com.udea.demo.pedidos.interfaces.services.AccesoPedidoConductorI;
import com.udea.demo.usuarios.application.service.ActorAuthorizationService;
import com.udea.demo.usuarios.domain.model.Rol;
import com.udea.demo.usuarios.domain.model.Usuario;
import com.udea.demo.usuarios.interfaces.persistence.ClienteRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class TrazabilidadUbicacionService {
    private final PedidoRepository pedidos;
    private final EventoUbicacionPedidoRepository eventos;
    private final JdbcTemplate jdbc;
    private final ActorAuthorizationService actores;
    private final ClienteRepository clientes;
    private final AccesoPedidoConductorI accesoConductor;
    private final RegistroHistorialPedido historial;
    private final AccesoClientePedido accesoCliente;

    public TrazabilidadUbicacionService(PedidoRepository pedidos, EventoUbicacionPedidoRepository eventos,
                                        JdbcTemplate jdbc, ActorAuthorizationService actores,
                                        ClienteRepository clientes, AccesoPedidoConductorI accesoConductor,
                                        RegistroHistorialPedido historial, AccesoClientePedido accesoCliente) {
        this.pedidos = pedidos;
        this.eventos = eventos;
        this.jdbc = jdbc;
        this.actores = actores;
        this.clientes = clientes;
        this.accesoConductor = accesoConductor;
        this.historial = historial;
        this.accesoCliente = accesoCliente;
    }

    @Transactional
    public UbicacionResponseDTO registrar(Long pedidoId, RegistrarUbicacionRequestDTO dto) {
        Usuario actor = actores.actorActual();
        if (actor.getRol() != Rol.OPERADOR && actor.getRol() != Rol.CONDUCTOR) {
            if (actor.getRol() != Rol.CLIENTE) {
                throw new AccessDeniedException("Solo un operador o conductor puede registrar la ubicación");
            }
        }
        Pedido pedido = pedidos.findByIdForUpdate(pedidoId)
                .orElseThrow(() -> new PedidoNoEncontradoException(pedidoId));
        if (actor.getRol() == Rol.OPERADOR) {
            actores.operadorActualId();
        } else if (actor.getRol() == Rol.CONDUCTOR) {
            actores.conductorActualUsuarioId();
            if (!accesoConductor.tieneAsignacionActiva(pedidoId, actor.getId())) {
                throw new AccessDeniedException("El envío no está asignado al conductor autenticado");
            }
        } else {
            throw new AccessDeniedException("Solo un operador o conductor puede registrar la ubicación");
        }

        validarQr(pedido, dto);
        var previo = eventos.findByIdEvento(dto.idEvento());
        if (previo.isPresent()) {
            if (!previo.get().getPedidoId().equals(pedidoId)
                    || !previo.get().getUsuarioId().equals(actor.getId())
                    || !coincide(previo.get(), dto)) {
                throw new IllegalArgumentException("El identificador del evento ya fue usado para otro registro");
            }
            return response(previo.get());
        }
        exigirTransicion(pedido, dto.etapa());
        LocalDateTime fechaEvento = dto.fechaEvento().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        if (fechaEvento.isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw new IllegalArgumentException("La fecha del evento no puede estar en el futuro");
        }

        var ciudad = jdbc.query("""
                SELECT id, nombre, departamento FROM ciudades_logisticas WHERE id = ? AND activa = true
                """, (rs, row) -> new CiudadResponseDTO(rs.getLong("id"), rs.getString("nombre"),
                rs.getString("departamento"), true), dto.ciudadId()).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("La ciudad no existe o está inactiva"));
        var oficina = resolverOficina(dto.oficinaId(), dto.ciudadId());
        var punto = resolverPunto(dto.puntoEntregaId(), dto.ciudadId(), dto.oficinaId());
        validarCamposUbicacion(dto);

        if (dto.corrigeEventoId() != null) {
            var original = eventos.findById(dto.corrigeEventoId())
                    .orElseThrow(() -> new IllegalArgumentException("No existe el evento que se desea corregir"));
            if (!original.getPedidoId().equals(pedidoId)) {
                throw new IllegalArgumentException("El evento a corregir pertenece a otro envío");
            }
        }

        EstadoPedido anterior = pedido.getEstado();
        EstadoPedido resultante = dto.etapa().estadoPedido();
        if (anterior != resultante) {
            if (!pedido.puedeCambiarEstadoLogisticoA(resultante)) {
                throw new TransicionEstadoInvalidaException(anterior, resultante);
            }
            pedido.cambiarEstadoLogistico(resultante);
            pedidos.save(pedido);
        }

        String direccion = punto != null ? punto.direccion()
                : oficina != null ? oficina.direccion() : pedido.getDireccionOrigen();
        EventoUbicacionPedido evento = eventos.saveAndFlush(new EventoUbicacionPedido(dto.idEvento(), pedidoId,
                pedido.getNumeroTracking(), dto.etapa(), resultante, ciudad.id(), ciudad.nombre(),
                oficina == null ? null : oficina.id(), oficina == null ? null : oficina.nombre(),
                punto == null ? null : punto.id(), punto == null ? null : punto.nombre(), direccion,
                normalizar(dto.vehiculoId()), actor.getId(), dto.origen(), fechaEvento, dto.corrigeEventoId()));
        historial.registrar(pedidoId, actor.getId(), RegistroHistorialPedido.ESTADO_LOGISTICO,
                "UBICACION_" + dto.origen().name(), dto.etapa().name(), fechaEvento);
        return response(evento);
    }

    @Transactional(readOnly = true)
    public List<UbicacionResponseDTO> consultar(Long pedidoId) {
        Pedido pedido = pedidos.findById(pedidoId).orElseThrow(() -> new PedidoNoEncontradoException(pedidoId));
        if (!esDba()) autorizarLectura(pedido, actores.actorActual());
        return eventos.findByPedidoIdOrderByFechaEventoAscIdAsc(pedidoId).stream()
                .map(TrazabilidadUbicacionService::response).toList();
    }

    @Transactional(readOnly = true)
    public List<UbicacionResponseDTO> consultarCliente(String numeroTracking) {
        Pedido pedido = accesoCliente.pedidoDelClienteActual(numeroTracking);
        return eventos.findByPedidoIdOrderByFechaEventoAscIdAsc(pedido.getId()).stream()
                .map(TrazabilidadUbicacionService::response).toList();
    }

    private static boolean esDba() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_DBA"));
    }

    private void autorizarLectura(Pedido pedido, Usuario actor) {
        if (actor.getRol() == Rol.OPERADOR) {
            actores.operadorActualId();
            return;
        }
        if (actor.getRol() == Rol.CLIENTE) {
            accesoCliente.pedidoDelClienteActual(pedido.getNumeroTracking());
            return;
        }
        if (actor.getRol() == Rol.CONDUCTOR) {
            actores.conductorActualUsuarioId();
            if (!accesoConductor.fueAsignado(pedido.getId(), actor.getId())) {
                throw new AccessDeniedException("El envío no fue asignado al conductor autenticado");
            }
            return;
        }
        throw new AccessDeniedException("El rol de la sesión no permite consultar la trazabilidad");
    }

    private static void exigirTransicion(Pedido pedido, EtapaUbicacion etapa) {
        EstadoPedido actual = pedido.getEstado();
        if (actual.esFinal()) throw new EnvioFinalizadoException(actual);
        boolean valida = switch (etapa) {
            case EN_BODEGA_ORIGEN -> actual == EstadoPedido.CREADO || actual == EstadoPedido.RECIBIDO_EN_ORIGEN;
            case EN_TRANSITO_VEHICULO -> actual == EstadoPedido.RECIBIDO_EN_ORIGEN
                    || actual == EstadoPedido.EN_TRANSITO;
            case LLEGADO_OFICINA_DESTINO -> actual == EstadoPedido.EN_TRANSITO;
            case EN_PUNTO_ENTREGA -> actual == EstadoPedido.EN_TRANSITO || actual == EstadoPedido.EN_REPARTO
                    || actual == EstadoPedido.ENTREGA_REPROGRAMADA
                    || actual == EstadoPedido.DIRECCION_POR_VERIFICAR;
        };
        if (!valida) throw new TransicionEstadoInvalidaException(actual, etapa.estadoPedido());
    }

    private static void validarQr(Pedido pedido, RegistrarUbicacionRequestDTO dto) {
        if (dto.origen() == OrigenUbicacion.QR) {
            CodigoQrEnvio qr = CodigoQrEnvio.parsear(dto.codigoQr());
            if (!qr.numeroTracking().equals(pedido.getNumeroTracking())) {
                throw new CodigoQrOtroEnvioException(pedido.getId());
            }
        } else if (dto.codigoQr() != null && !dto.codigoQr().isBlank()) {
            throw new CodigoQrInvalidoException();
        }
    }

    private Oficina resolverOficina(Long oficinaId, Long ciudadId) {
        if (oficinaId == null) return null;
        return jdbc.query("""
                SELECT id, nombre, direccion FROM oficinas_logisticas
                WHERE id = ? AND ciudad_id = ? AND activa = true
                """, (rs, row) -> new Oficina(rs.getLong("id"), rs.getString("nombre"),
                rs.getString("direccion")), oficinaId, ciudadId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("La oficina no pertenece a la ciudad o está inactiva"));
    }

    private Punto resolverPunto(Long puntoId, Long ciudadId, Long oficinaId) {
        if (puntoId == null) return null;
        return jdbc.query("""
                SELECT id, nombre, direccion FROM puntos_entrega
                WHERE id = ? AND ciudad_id = ? AND activo = true
                  AND (? IS NULL OR oficina_id = ?)
                """, (rs, row) -> new Punto(rs.getLong("id"), rs.getString("nombre"),
                rs.getString("direccion")), puntoId, ciudadId, oficinaId, oficinaId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El punto no corresponde a la ubicación o está inactivo"));
    }

    private static void validarCamposUbicacion(RegistrarUbicacionRequestDTO dto) {
        switch (dto.etapa()) {
            case EN_BODEGA_ORIGEN, LLEGADO_OFICINA_DESTINO -> {
                if (dto.oficinaId() == null) throw new IllegalArgumentException("Selecciona la oficina del evento");
            }
            case EN_TRANSITO_VEHICULO -> {
                if (dto.vehiculoId() == null || dto.vehiculoId().isBlank()) {
                    throw new IllegalArgumentException("El identificador del vehículo es obligatorio en tránsito");
                }
            }
            case EN_PUNTO_ENTREGA -> {
                if (dto.puntoEntregaId() == null) throw new IllegalArgumentException("Selecciona el punto de entrega");
            }
        }
        if (dto.origen() == OrigenUbicacion.QR && (dto.codigoQr() == null || dto.codigoQr().isBlank())) {
            throw new CodigoQrInvalidoException();
        }
    }

    private static UbicacionResponseDTO response(EventoUbicacionPedido evento) {
        return new UbicacionResponseDTO(evento.getId(), evento.getPedidoId(), evento.getNumeroTracking(),
                evento.getEtapa(), evento.getEstadoPedido(), evento.getCiudadId(), evento.getCiudad(),
                evento.getOficinaId(), evento.getOficina(), evento.getPuntoEntregaId(),
                evento.getPuntoEntrega(), evento.getDireccion(), evento.getVehiculoId(), evento.getUsuarioId(),
                evento.getOrigen(), evento.getFechaEvento(), evento.getFechaRegistro(), evento.getCorrigeEventoId());
    }

    private static boolean coincide(EventoUbicacionPedido evento, RegistrarUbicacionRequestDTO dto) {
        return evento.getEtapa() == dto.etapa()
                && evento.getOrigen() == dto.origen()
                && java.util.Objects.equals(evento.getCiudadId(), dto.ciudadId())
                && java.util.Objects.equals(evento.getOficinaId(), dto.oficinaId())
                && java.util.Objects.equals(evento.getPuntoEntregaId(), dto.puntoEntregaId())
                && java.util.Objects.equals(evento.getVehiculoId(), normalizar(dto.vehiculoId()))
                && java.util.Objects.equals(evento.getCorrigeEventoId(), dto.corrigeEventoId());
    }

    private static String normalizar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private record Oficina(Long id, String nombre, String direccion) {}
    private record Punto(Long id, String nombre, String direccion) {}
}

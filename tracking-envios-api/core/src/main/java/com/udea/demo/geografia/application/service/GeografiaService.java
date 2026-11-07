package com.udea.demo.geografia.application.service;

import com.udea.demo.geografia.application.dto.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GeografiaService {
    private final JdbcTemplate jdbc;

    public GeografiaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<CiudadResponseDTO> ciudades(boolean incluirInactivas) {
        return jdbc.query("""
                SELECT id, nombre, departamento, activa FROM ciudades_logisticas
                WHERE (? = true OR activa = true) ORDER BY departamento, nombre
                """, (rs, row) -> new CiudadResponseDTO(rs.getLong("id"), rs.getString("nombre"),
                rs.getString("departamento"), rs.getBoolean("activa")), incluirInactivas);
    }

    @Transactional
    public CiudadResponseDTO crearCiudad(CiudadRequestDTO dto) {
        exigirNombreCiudadUnico(dto.nombre(), dto.departamento(), null);
        long id = insertar("""
                INSERT INTO ciudades_logisticas (nombre, departamento, activa) VALUES (?, ?, true)
                """, dto.nombre().trim(), dto.departamento().trim());
        return ciudad(id);
    }

    @Transactional
    public CiudadResponseDTO actualizarCiudad(long id, CiudadRequestDTO dto) {
        exigirNombreCiudadUnico(dto.nombre(), dto.departamento(), id);
        exigirActualizacion(jdbc.update("""
                UPDATE ciudades_logisticas SET nombre = ?, departamento = ?
                WHERE id = ? AND activa = true
                """, dto.nombre().trim(), dto.departamento().trim(), id), "Ciudad no encontrada o inactiva");
        return ciudad(id);
    }

    @Transactional
    public void activarCiudad(long id, boolean activa) {
        exigirActualizacion(jdbc.update("UPDATE ciudades_logisticas SET activa = ? WHERE id = ?", activa, id),
                "Ciudad no encontrada");
    }

    @Transactional(readOnly = true)
    public List<OficinaResponseDTO> oficinas(Long ciudadId, boolean incluirInactivas) {
        String sql = """
                SELECT o.id, o.nombre, o.ciudad_id, c.nombre AS ciudad, c.departamento,
                       o.direccion, o.telefono, o.email, o.activa
                FROM oficinas_logisticas o JOIN ciudades_logisticas c ON c.id = o.ciudad_id
                WHERE (? = true OR (o.activa = true AND c.activa = true))
                """;
        Object[] parametros;
        if (ciudadId == null) {
            parametros = new Object[]{incluirInactivas};
        } else {
            sql += " AND o.ciudad_id = ?";
            parametros = new Object[]{incluirInactivas, ciudadId};
        }
        sql += " ORDER BY c.nombre, o.nombre";
        return jdbc.query(sql, (rs, row) -> new OficinaResponseDTO(rs.getLong("id"), rs.getString("nombre"),
                rs.getLong("ciudad_id"), rs.getString("ciudad"), rs.getString("departamento"),
                rs.getString("direccion"), rs.getString("telefono"), rs.getString("email"),
                rs.getBoolean("activa")), parametros);
    }

    @Transactional
    public OficinaResponseDTO crearOficina(OficinaRequestDTO dto) {
        exigirCiudadActiva(dto.ciudadId());
        exigirOficinaUnica(dto.nombre(), dto.direccion(), dto.ciudadId(), null);
        long id = insertar("""
                INSERT INTO oficinas_logisticas (nombre, ciudad_id, direccion, telefono, email, activa)
                VALUES (?, ?, ?, ?, ?, true)
                """, dto.nombre().trim(), dto.ciudadId(), dto.direccion().trim(), dto.telefono().trim(),
                normalizar(dto.email()));
        return oficina(id);
    }

    @Transactional
    public OficinaResponseDTO actualizarOficina(long id, OficinaRequestDTO dto) {
        exigirCiudadActiva(dto.ciudadId());
        exigirOficinaUnica(dto.nombre(), dto.direccion(), dto.ciudadId(), id);
        exigirActualizacion(jdbc.update("""
                UPDATE oficinas_logisticas SET nombre = ?, ciudad_id = ?, direccion = ?, telefono = ?, email = ?
                WHERE id = ? AND activa = true
                """, dto.nombre().trim(), dto.ciudadId(), dto.direccion().trim(), dto.telefono().trim(),
                normalizar(dto.email()), id), "Oficina no encontrada o inactiva");
        return oficina(id);
    }

    @Transactional
    public void activarOficina(long id, boolean activa) {
        exigirActualizacion(jdbc.update("UPDATE oficinas_logisticas SET activa = ? WHERE id = ?", activa, id),
                "Oficina no encontrada");
    }

    @Transactional(readOnly = true)
    public List<PuntoEntregaResponseDTO> puntos(Long ciudadId, Long oficinaId, boolean incluirInactivas) {
        boolean mostrarInactivas = incluirInactivas;
        String sql = """
                SELECT p.id, p.nombre, p.ciudad_id, c.nombre AS ciudad, p.oficina_id,
                       o.nombre AS oficina, p.direccion, p.descripcion, p.activo
                FROM puntos_entrega p
                JOIN ciudades_logisticas c ON c.id = p.ciudad_id
                LEFT JOIN oficinas_logisticas o ON o.id = p.oficina_id
                WHERE (? = true OR (p.activo = true AND c.activa = true
                    AND (p.oficina_id IS NULL OR o.activa = true)))
                """;
        var parametros = new java.util.ArrayList<Object>();
        parametros.add(mostrarInactivas);
        if (ciudadId != null) {
            sql += " AND p.ciudad_id = ?";
            parametros.add(ciudadId);
        }
        if (oficinaId != null) {
            sql += " AND p.oficina_id = ?";
            parametros.add(oficinaId);
        }
        sql += " ORDER BY c.nombre, p.nombre";
        return jdbc.query(sql, (rs, row) -> new PuntoEntregaResponseDTO(rs.getLong("id"), rs.getString("nombre"),
                rs.getLong("ciudad_id"), rs.getString("ciudad"), (Long) rs.getObject("oficina_id"),
                rs.getString("oficina"), rs.getString("direccion"), rs.getString("descripcion"),
                rs.getBoolean("activo")), parametros.toArray());
    }

    @Transactional
    public PuntoEntregaResponseDTO crearPunto(PuntoEntregaRequestDTO dto) {
        validarCiudadOficina(dto.ciudadId(), dto.oficinaId());
        exigirPuntoUnico(dto.nombre(), dto.direccion(), dto.ciudadId(), dto.oficinaId(), null);
        long id = insertar("""
                INSERT INTO puntos_entrega (nombre, ciudad_id, oficina_id, direccion, descripcion, activo)
                VALUES (?, ?, ?, ?, ?, true)
                """, dto.nombre().trim(), dto.ciudadId(), dto.oficinaId(), dto.direccion().trim(),
                normalizar(dto.descripcion()));
        return punto(id);
    }

    @Transactional
    public PuntoEntregaResponseDTO actualizarPunto(long id, PuntoEntregaRequestDTO dto) {
        validarCiudadOficina(dto.ciudadId(), dto.oficinaId());
        exigirPuntoUnico(dto.nombre(), dto.direccion(), dto.ciudadId(), dto.oficinaId(), id);
        exigirActualizacion(jdbc.update("""
                UPDATE puntos_entrega SET nombre = ?, ciudad_id = ?, oficina_id = ?, direccion = ?, descripcion = ?
                WHERE id = ? AND activo = true
                """, dto.nombre().trim(), dto.ciudadId(), dto.oficinaId(), dto.direccion().trim(),
                normalizar(dto.descripcion()), id), "Punto de entrega no encontrado o inactivo");
        return punto(id);
    }

    @Transactional
    public void activarPunto(long id, boolean activo) {
        exigirActualizacion(jdbc.update("UPDATE puntos_entrega SET activo = ? WHERE id = ?", activo, id),
                "Punto de entrega no encontrado");
    }

    private CiudadResponseDTO ciudad(long id) {
        return jdbc.queryForObject("""
                SELECT id, nombre, departamento, activa FROM ciudades_logisticas WHERE id = ?
                """, (rs, row) -> new CiudadResponseDTO(id, rs.getString("nombre"),
                rs.getString("departamento"), rs.getBoolean("activa")), id);
    }

    private OficinaResponseDTO oficina(long id) {
        return jdbc.queryForObject("""
                SELECT o.nombre, o.ciudad_id, c.nombre AS ciudad, c.departamento, o.direccion,
                       o.telefono, o.email, o.activa
                FROM oficinas_logisticas o JOIN ciudades_logisticas c ON c.id = o.ciudad_id WHERE o.id = ?
                """, (rs, row) -> new OficinaResponseDTO(id, rs.getString("nombre"), rs.getLong("ciudad_id"),
                rs.getString("ciudad"), rs.getString("departamento"), rs.getString("direccion"),
                rs.getString("telefono"), rs.getString("email"), rs.getBoolean("activa")), id);
    }

    private PuntoEntregaResponseDTO punto(long id) {
        return jdbc.queryForObject("""
                SELECT p.nombre, p.ciudad_id, c.nombre AS ciudad, p.oficina_id, o.nombre AS oficina,
                       p.direccion, p.descripcion, p.activo
                FROM puntos_entrega p JOIN ciudades_logisticas c ON c.id = p.ciudad_id
                LEFT JOIN oficinas_logisticas o ON o.id = p.oficina_id WHERE p.id = ?
                """, (rs, row) -> new PuntoEntregaResponseDTO(id, rs.getString("nombre"),
                rs.getLong("ciudad_id"), rs.getString("ciudad"), (Long) rs.getObject("oficina_id"),
                rs.getString("oficina"), rs.getString("direccion"), rs.getString("descripcion"),
                rs.getBoolean("activo")), id);
    }

    private long insertar(String sql, Object... parametros) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update((PreparedStatementCreator) connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < parametros.length; i++) statement.setObject(i + 1, parametros[i]);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("La base de datos no devolvió el identificador geográfico");
        return key.longValue();
    }

    private void exigirCiudadActiva(Long ciudadId) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM ciudades_logisticas WHERE id = ? AND activa = true)",
                Boolean.class, ciudadId))) {
            throw new IllegalArgumentException("La ciudad no existe o está inactiva");
        }
    }

    private void validarCiudadOficina(Long ciudadId, Long oficinaId) {
        exigirCiudadActiva(ciudadId);
        if (oficinaId == null) return;
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM oficinas_logisticas
                WHERE id = ? AND ciudad_id = ? AND activa = true)
                """, Boolean.class, oficinaId, ciudadId))) {
            throw new IllegalArgumentException("La oficina no pertenece a la ciudad o está inactiva");
        }
    }

    private void exigirNombreCiudadUnico(String nombre, String departamento, Long excluirId) {
        if (existe("""
                SELECT EXISTS(SELECT 1 FROM ciudades_logisticas
                WHERE LOWER(TRIM(nombre)) = LOWER(TRIM(?))
                  AND LOWER(TRIM(departamento)) = LOWER(TRIM(?))
                  AND (? IS NULL OR id <> ?))
                """, nombre, departamento, excluirId, excluirId)) {
            throw new IllegalArgumentException("Ya existe una ciudad con ese nombre y departamento");
        }
    }

    private void exigirOficinaUnica(String nombre, String direccion, Long ciudadId, Long excluirId) {
        if (existe("""
                SELECT EXISTS(SELECT 1 FROM oficinas_logisticas
                WHERE ciudad_id = ? AND LOWER(TRIM(nombre)) = LOWER(TRIM(?))
                  AND (? IS NULL OR id <> ?))
                """, ciudadId, nombre, excluirId, excluirId)) {
            throw new IllegalArgumentException("Ya existe una oficina con ese nombre en la ciudad");
        }
        if (existe("""
                SELECT EXISTS(SELECT 1 FROM oficinas_logisticas
                WHERE ciudad_id = ? AND LOWER(TRIM(direccion)) = LOWER(TRIM(?))
                  AND (? IS NULL OR id <> ?))
                """, ciudadId, direccion, excluirId, excluirId)) {
            throw new IllegalArgumentException("Ya existe una oficina con esa dirección en la ciudad");
        }
    }

    private void exigirPuntoUnico(String nombre, String direccion, Long ciudadId,
                                  Long oficinaId, Long excluirId) {
        String scope = "ciudad_id = ? AND (oficina_id = ? OR (oficina_id IS NULL AND ? IS NULL))";
        if (existe("SELECT EXISTS(SELECT 1 FROM puntos_entrega WHERE " + scope
                        + " AND LOWER(TRIM(nombre)) = LOWER(TRIM(?)) AND (? IS NULL OR id <> ?))",
                ciudadId, oficinaId, oficinaId, nombre, excluirId, excluirId)) {
            throw new IllegalArgumentException("Ya existe un punto con ese nombre en la ubicación");
        }
        if (existe("SELECT EXISTS(SELECT 1 FROM puntos_entrega WHERE " + scope
                        + " AND LOWER(TRIM(direccion)) = LOWER(TRIM(?)) AND (? IS NULL OR id <> ?))",
                ciudadId, oficinaId, oficinaId, direccion, excluirId, excluirId)) {
            throw new IllegalArgumentException("Ya existe un punto con esa dirección en la ubicación");
        }
    }

    private boolean existe(String sql, Object... parametros) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, parametros));
    }

    private static void exigirActualizacion(int filas, String mensaje) {
        if (filas == 0) throw new IllegalArgumentException(mensaje);
    }

    private static String normalizar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}

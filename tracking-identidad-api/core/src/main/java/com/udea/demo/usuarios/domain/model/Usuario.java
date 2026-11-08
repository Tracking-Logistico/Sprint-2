package com.udea.demo.usuarios.domain.model;

import com.udea.demo.usuarios.domain.exception.DatosIdentidadInmutablesException;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    private String telefono;
    private String direccion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoUsuario estado;

    @Column(nullable = false)
    private Boolean aceptoTerminos;

    private LocalDateTime fechaAceptacionTerminos;
    private String versionTerminos;

    @Column(nullable = false)
    private Boolean aceptoPoliticaDatos = false;
    private LocalDateTime fechaAceptacionPoliticaDatos;
    private String versionPoliticaDatos;

    private Boolean activo = true;

    private LocalDateTime fechaCreacion;

    // HU-13 (V24). Solo se guardan cifrados (AES-256-GCM) y con un índice ciego. No tienen setters: la única
    // forma de asignarlos es registrarDatosIdentidad, que los deja bloqueados (CA-3).
    @Column(name = "numero_cedula_cifrado", length = 255)
    private String numeroCedulaCifrado;

    @Column(name = "numero_cedula_hash", length = 64, unique = true)
    private String numeroCedulaHash;

    @Column(name = "fecha_nacimiento_cifrada", length = 255)
    private String fechaNacimientoCifrada;

    @Column(name = "datos_identidad_bloqueados", nullable = false)
    private Boolean datosIdentidadBloqueados = false;

    /** Momento del bloqueo, en UTC. */
    @Column(name = "fecha_bloqueo_identidad")
    private LocalDateTime fechaBloqueoIdentidad;

    @PrePersist
    public void prePersist() {
        this.fechaCreacion = LocalDateTime.now();
    }

    public Usuario() {}
    public Usuario(Long id, String nombre, String email, String password, String telefono, String direccion, Rol rol, EstadoUsuario estado, Boolean aceptoTerminos, LocalDateTime fechaAceptacionTerminos, String versionTerminos, Boolean aceptoPoliticaDatos, LocalDateTime fechaAceptacionPoliticaDatos, String versionPoliticaDatos, Boolean activo, LocalDateTime fechaCreacion) {
        this.id = id;
        this.nombre = nombre;
        this.email = email;
        this.password = password;
        this.telefono = telefono;
        this.direccion = direccion;
        this.rol = rol;
        this.estado = estado;
        this.aceptoTerminos = aceptoTerminos;
        this.fechaAceptacionTerminos = fechaAceptacionTerminos;
        this.versionTerminos = versionTerminos;
        this.aceptoPoliticaDatos = aceptoPoliticaDatos;
        this.fechaAceptacionPoliticaDatos = fechaAceptacionPoliticaDatos;
        this.versionPoliticaDatos = versionPoliticaDatos;
        this.activo = activo;
        this.fechaCreacion = fechaCreacion;
    }

    public boolean puedeAutenticarse() {
        return Boolean.TRUE.equals(activo)
                && (estado == EstadoUsuario.ACTIVO
                    || estado == EstadoUsuario.PENDIENTE_ACTIVACION
                    || (rol == Rol.CLIENTE && estado == EstadoUsuario.PENDIENTE_VERIFICACION));
    }

    /**
     * Registra la cédula y la fecha de nacimiento, ya protegidas, y las bloquea (CA-3). Solo se puede hacer
     * una vez. Si dos peticiones concurrentes compiten por el mismo usuario, el trigger
     * trg_usuarios_identidad_inmutable de V24 rechaza la segunda.
     *
     * @throws DatosIdentidadInmutablesException si el usuario ya tiene datos de identidad registrados
     */
    public void registrarDatosIdentidad(DatosIdentidadProtegidos datos, Instant ahora) {
        Objects.requireNonNull(datos, "datos");
        Objects.requireNonNull(ahora, "ahora");
        if (tieneDatosIdentidad()) {
            throw new DatosIdentidadInmutablesException();
        }
        this.numeroCedulaCifrado = datos.numeroCedulaCifrado();
        this.numeroCedulaHash = datos.numeroCedulaHash();
        this.fechaNacimientoCifrada = datos.fechaNacimientoCifrada();
        this.datosIdentidadBloqueados = true;
        this.fechaBloqueoIdentidad = LocalDateTime.ofInstant(ahora, ZoneOffset.UTC);
    }

    public boolean tieneDatosIdentidad() {
        return Boolean.TRUE.equals(datosIdentidadBloqueados) || numeroCedulaHash != null;
    }

    public String getNumeroCedulaCifrado() { return numeroCedulaCifrado; }
    public String getNumeroCedulaHash() { return numeroCedulaHash; }
    public String getFechaNacimientoCifrada() { return fechaNacimientoCifrada; }
    public boolean isDatosIdentidadBloqueados() { return Boolean.TRUE.equals(datosIdentidadBloqueados); }
    public LocalDateTime getFechaBloqueoIdentidad() { return fechaBloqueoIdentidad; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }
    public EstadoUsuario getEstado() { return estado; }
    public void setEstado(EstadoUsuario estado) { this.estado = estado; }
    public Boolean getAceptoTerminos() { return aceptoTerminos; }
    public void setAceptoTerminos(Boolean aceptoTerminos) { this.aceptoTerminos = aceptoTerminos; }
    public LocalDateTime getFechaAceptacionTerminos() { return fechaAceptacionTerminos; }
    public void setFechaAceptacionTerminos(LocalDateTime fechaAceptacionTerminos) { this.fechaAceptacionTerminos = fechaAceptacionTerminos; }
    public String getVersionTerminos() { return versionTerminos; }
    public void setVersionTerminos(String versionTerminos) { this.versionTerminos = versionTerminos; }
    public Boolean getAceptoPoliticaDatos() { return aceptoPoliticaDatos; }
    public void setAceptoPoliticaDatos(Boolean aceptoPoliticaDatos) { this.aceptoPoliticaDatos = aceptoPoliticaDatos; }
    public LocalDateTime getFechaAceptacionPoliticaDatos() { return fechaAceptacionPoliticaDatos; }
    public void setFechaAceptacionPoliticaDatos(LocalDateTime fechaAceptacionPoliticaDatos) { this.fechaAceptacionPoliticaDatos = fechaAceptacionPoliticaDatos; }
    public String getVersionPoliticaDatos() { return versionPoliticaDatos; }
    public void setVersionPoliticaDatos(String versionPoliticaDatos) { this.versionPoliticaDatos = versionPoliticaDatos; }
    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public static UsuarioBuilder builder() { return new UsuarioBuilder(); }
    public static class UsuarioBuilder {
        private Long id;
        private String nombre;
        private String email;
        private String password;
        private String telefono;
        private String direccion;
        private Rol rol;
        private EstadoUsuario estado;
        private Boolean aceptoTerminos;
        private LocalDateTime fechaAceptacionTerminos;
        private String versionTerminos;
        private Boolean aceptoPoliticaDatos;
        private boolean aceptoPoliticaDatosSet;
        private LocalDateTime fechaAceptacionPoliticaDatos;
        private String versionPoliticaDatos;
        private Boolean activo;
        private boolean activoSet;
        private LocalDateTime fechaCreacion;

        public UsuarioBuilder id(Long id) { this.id = id; return this; }
        public UsuarioBuilder nombre(String nombre) { this.nombre = nombre; return this; }
        public UsuarioBuilder email(String email) { this.email = email; return this; }
        public UsuarioBuilder password(String password) { this.password = password; return this; }
        public UsuarioBuilder telefono(String telefono) { this.telefono = telefono; return this; }
        public UsuarioBuilder direccion(String direccion) { this.direccion = direccion; return this; }
        public UsuarioBuilder rol(Rol rol) { this.rol = rol; return this; }
        public UsuarioBuilder estado(EstadoUsuario estado) { this.estado = estado; return this; }
        public UsuarioBuilder aceptoTerminos(Boolean aceptoTerminos) { this.aceptoTerminos = aceptoTerminos; return this; }
        public UsuarioBuilder fechaAceptacionTerminos(LocalDateTime fechaAceptacionTerminos) { this.fechaAceptacionTerminos = fechaAceptacionTerminos; return this; }
        public UsuarioBuilder versionTerminos(String versionTerminos) { this.versionTerminos = versionTerminos; return this; }
        public UsuarioBuilder aceptoPoliticaDatos(Boolean aceptoPoliticaDatos) { this.aceptoPoliticaDatos = aceptoPoliticaDatos; this.aceptoPoliticaDatosSet = true; return this; }
        public UsuarioBuilder fechaAceptacionPoliticaDatos(LocalDateTime fechaAceptacionPoliticaDatos) { this.fechaAceptacionPoliticaDatos = fechaAceptacionPoliticaDatos; return this; }
        public UsuarioBuilder versionPoliticaDatos(String versionPoliticaDatos) { this.versionPoliticaDatos = versionPoliticaDatos; return this; }
        public UsuarioBuilder activo(Boolean activo) { this.activo = activo; this.activoSet = true; return this; }
        public UsuarioBuilder fechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; return this; }
        public Usuario build() {
            Boolean aceptoPoliticaDatosValue = aceptoPoliticaDatosSet ? aceptoPoliticaDatos : false;
            Boolean activoValue = activoSet ? activo : true;
            return new Usuario(id, nombre, email, password, telefono, direccion, rol, estado, aceptoTerminos, fechaAceptacionTerminos, versionTerminos, aceptoPoliticaDatosValue, fechaAceptacionPoliticaDatos, versionPoliticaDatos, activoValue, fechaCreacion);
        }
    }
}

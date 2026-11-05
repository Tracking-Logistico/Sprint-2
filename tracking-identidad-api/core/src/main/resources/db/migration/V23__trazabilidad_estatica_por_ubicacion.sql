-- HU-19: catálogos geográficos con desactivación lógica y ubicaciones históricas inmutables.
CREATE TABLE ciudades_logisticas (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    departamento VARCHAR(100) NOT NULL,
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_ciudad_nombre_departamento
    ON ciudades_logisticas (LOWER(TRIM(nombre)), LOWER(TRIM(departamento)));

CREATE TABLE oficinas_logisticas (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL,
    ciudad_id BIGINT NOT NULL REFERENCES ciudades_logisticas(id) ON DELETE RESTRICT,
    direccion VARCHAR(250) NOT NULL,
    telefono VARCHAR(20) NOT NULL,
    email VARCHAR(255),
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_oficina_nombre_ciudad
    ON oficinas_logisticas (ciudad_id, LOWER(TRIM(nombre)));
CREATE UNIQUE INDEX uk_oficina_direccion_ciudad
    ON oficinas_logisticas (ciudad_id, LOWER(TRIM(direccion)));

CREATE TABLE puntos_entrega (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(120) NOT NULL,
    ciudad_id BIGINT NOT NULL REFERENCES ciudades_logisticas(id) ON DELETE RESTRICT,
    oficina_id BIGINT REFERENCES oficinas_logisticas(id) ON DELETE RESTRICT,
    direccion VARCHAR(250) NOT NULL,
    descripcion VARCHAR(500),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uk_punto_entrega_nombre_ciudad
    ON puntos_entrega (ciudad_id, COALESCE(oficina_id, 0), LOWER(TRIM(nombre)));
CREATE UNIQUE INDEX uk_punto_entrega_direccion_ciudad
    ON puntos_entrega (ciudad_id, COALESCE(oficina_id, 0), LOWER(TRIM(direccion)));

CREATE TABLE eventos_ubicacion_pedido (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_evento VARCHAR(36) NOT NULL UNIQUE,
    id_pedido BIGINT NOT NULL REFERENCES pedidos(id_pedido) ON DELETE RESTRICT,
    numero_tracking VARCHAR(50) NOT NULL,
    etapa VARCHAR(40) NOT NULL CHECK (etapa IN (
        'EN_BODEGA_ORIGEN','EN_TRANSITO_VEHICULO','LLEGADO_OFICINA_DESTINO','EN_PUNTO_ENTREGA'
    )),
    estado_pedido VARCHAR(40) NOT NULL,
    ciudad_id BIGINT REFERENCES ciudades_logisticas(id) ON DELETE RESTRICT,
    ciudad_nombre VARCHAR(100) NOT NULL,
    oficina_id BIGINT REFERENCES oficinas_logisticas(id) ON DELETE RESTRICT,
    oficina_nombre VARCHAR(120),
    punto_entrega_id BIGINT REFERENCES puntos_entrega(id) ON DELETE RESTRICT,
    punto_entrega_nombre VARCHAR(120),
    direccion VARCHAR(250),
    vehiculo_id VARCHAR(80),
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE RESTRICT,
    origen VARCHAR(20) NOT NULL CHECK (origen IN ('WEB','APP_MOVIL','QR','SELECTOR')),
    fecha_evento TIMESTAMP NOT NULL,
    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    corrige_evento_id BIGINT REFERENCES eventos_ubicacion_pedido(id) ON DELETE RESTRICT
);
CREATE INDEX idx_evento_ubicacion_pedido_fecha
    ON eventos_ubicacion_pedido(id_pedido, fecha_evento, id);

CREATE FUNCTION impedir_modificacion_evento_ubicacion() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Los eventos de ubicación son inmutables; registre una corrección como un evento nuevo';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_eventos_ubicacion_append_only
    BEFORE UPDATE OR DELETE ON eventos_ubicacion_pedido
    FOR EACH ROW EXECUTE FUNCTION impedir_modificacion_evento_ubicacion();

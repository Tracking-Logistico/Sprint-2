CREATE TABLE assistant_session (
    id UUID PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    creada_en TIMESTAMP WITH TIME ZONE NOT NULL,
    expira_en TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (id,usuario_id),
    CHECK (expira_en>creada_en)
);
CREATE TABLE assistant_query_log (
    solicitud_id UUID NOT NULL,
    session_id UUID NOT NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id),
    fecha TIMESTAMP WITH TIME ZONE NOT NULL,
    intencion VARCHAR(24) NOT NULL CHECK (intencion IN ('PERFIL','PAQUETE','FUERA_DE_ALCANCE')),
    resultado VARCHAR(24) NOT NULL CHECK (resultado IN ('RESUELTA','FUERA_DE_ALCANCE','SIN_DATOS','ERROR')),
    origen VARCHAR(12) NOT NULL CHECK (origen IN ('WEB','MOVIL','QR','OFFLINE')),
    PRIMARY KEY (usuario_id,solicitud_id),
    FOREIGN KEY (session_id,usuario_id) REFERENCES assistant_session(id,usuario_id)
);
CREATE INDEX idx_assistant_query_fecha ON assistant_query_log(fecha);
CREATE INDEX idx_assistant_query_session ON assistant_query_log(session_id);
CREATE TABLE assistant_rate_limit (
    usuario_id BIGINT PRIMARY KEY REFERENCES usuarios(id),
    minuto BIGINT NOT NULL,
    peticiones INTEGER NOT NULL CHECK (peticiones BETWEEN 1 AND 31)
);
CREATE VIEW assistant_user_profile AS
    SELECT id,nombre,rol,estado,email,telefono,numero_cedula_cifrado FROM usuarios;
CREATE VIEW assistant_package AS
    SELECT id_pedido,numero_tracking,estado FROM pedidos WHERE numero_tracking IS NOT NULL;
CREATE VIEW assistant_location AS
    SELECT e.id,e.id_pedido,e.etapa,e.oficina_nombre,e.vehiculo_id,e.punto_entrega_nombre,e.ciudad_nombre,e.fecha_evento
    FROM eventos_ubicacion_pedido e WHERE NOT EXISTS
    (SELECT 1 FROM eventos_ubicacion_pedido c WHERE c.corrige_evento_id=e.id);

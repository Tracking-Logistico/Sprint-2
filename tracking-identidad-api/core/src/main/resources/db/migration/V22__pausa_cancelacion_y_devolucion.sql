-- HU-17: pausa, cancelación y tracking independiente para devoluciones.
ALTER TABLE pedidos ADD COLUMN estado_antes_pausa VARCHAR(40);
ALTER TABLE pedidos ADD COLUMN id_pedido_origen_devolucion BIGINT;
ALTER TABLE pedidos ADD COLUMN numero_tracking_origen_devolucion VARCHAR(50);

ALTER TABLE pedidos ADD CONSTRAINT fk_pedidos_origen_devolucion
    FOREIGN KEY (id_pedido_origen_devolucion) REFERENCES pedidos(id_pedido) ON DELETE RESTRICT;
ALTER TABLE pedidos ADD CONSTRAINT uk_pedidos_origen_devolucion
    UNIQUE (id_pedido_origen_devolucion);

ALTER TABLE pedidos DROP CONSTRAINT IF EXISTS chk_pedidos_estado;
ALTER TABLE pedidos ADD CONSTRAINT chk_pedidos_estado CHECK (estado IN (
    'SOLICITADO','CORRECCION_SOLICITADA','CREADO','RECIBIDO_EN_ORIGEN','EN_TRANSITO','EN_REPARTO','ENTREGADO',
    'RECHAZADO','ENTREGA_FALLIDA','ENTREGA_REPROGRAMADA','DIRECCION_POR_VERIFICAR','DEVOLUCION_AL_REMITENTE',
    'ENTREGA_FALLIDA_CERRADA','PAUSADO','CANCELADO'
));

ALTER TABLE notificaciones_conductor ADD COLUMN event_id VARCHAR(36);
ALTER TABLE notificaciones_conductor ADD CONSTRAINT uk_notificaciones_conductor_evento UNIQUE (event_id);

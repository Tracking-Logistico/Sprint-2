package com.udea.demo.pedidos.domain.model;

public enum EtapaUbicacion {
    EN_BODEGA_ORIGEN(EstadoPedido.RECIBIDO_EN_ORIGEN),
    EN_TRANSITO_VEHICULO(EstadoPedido.EN_TRANSITO),
    LLEGADO_OFICINA_DESTINO(EstadoPedido.EN_TRANSITO),
    EN_PUNTO_ENTREGA(EstadoPedido.EN_REPARTO);

    private final EstadoPedido estadoPedido;

    EtapaUbicacion(EstadoPedido estadoPedido) {
        this.estadoPedido = estadoPedido;
    }

    public EstadoPedido estadoPedido() {
        return estadoPedido;
    }
}

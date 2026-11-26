--liquibase formatted sql
--changeset otus:delivery-001
CREATE TABLE couriers (
    courier_id VARCHAR(100) PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE delivery_reservations (
    order_id BIGINT PRIMARY KEY,
    courier_id VARCHAR(100) NOT NULL REFERENCES couriers(courier_id),
    slot VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_courier_slot UNIQUE (courier_id, slot)
);
INSERT INTO couriers(courier_id) VALUES ('courier-001');
--rollback DROP TABLE delivery_reservations;
--rollback DROP TABLE couriers;

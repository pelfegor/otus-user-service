--liquibase formatted sql
--changeset otus:inventory-001
CREATE TABLE inventory_stock (
    sku VARCHAR(100) PRIMARY KEY,
    available INTEGER NOT NULL CHECK (available >= 0)
);
CREATE TABLE inventory_reservations (
    order_id BIGINT PRIMARY KEY,
    sku VARCHAR(100) NOT NULL REFERENCES inventory_stock(sku),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO inventory_stock(sku, available) VALUES ('SKU-001', 100);
--rollback DROP TABLE inventory_reservations;
--rollback DROP TABLE inventory_stock;

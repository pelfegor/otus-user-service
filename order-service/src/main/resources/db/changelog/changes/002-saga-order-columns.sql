--liquibase formatted sql
--changeset otus:orders-saga-002
ALTER TABLE customer_orders ADD COLUMN sku VARCHAR(100) NOT NULL DEFAULT 'legacy';
ALTER TABLE customer_orders ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0);
ALTER TABLE customer_orders ADD COLUMN delivery_slot VARCHAR(100) NOT NULL DEFAULT 'legacy';
ALTER TABLE customer_orders DROP CONSTRAINT customer_orders_status_check;
ALTER TABLE customer_orders ADD CONSTRAINT customer_orders_status_check
    CHECK (status IN ('PENDING', 'PAID', 'PAYMENT_FAILED', 'CONFIRMED', 'FAILED', 'COMPENSATION_REQUIRED'));
--rollback ALTER TABLE customer_orders DROP COLUMN delivery_slot;
--rollback ALTER TABLE customer_orders DROP COLUMN quantity;
--rollback ALTER TABLE customer_orders DROP COLUMN sku;

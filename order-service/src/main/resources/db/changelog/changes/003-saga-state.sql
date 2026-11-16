--liquibase formatted sql
--changeset otus:orders-saga-state-003
ALTER TABLE customer_orders ADD COLUMN courier_id VARCHAR(100);
CREATE TABLE order_saga_steps (
    order_id BIGINT NOT NULL REFERENCES customer_orders(id) ON DELETE CASCADE,
    step VARCHAR(30) NOT NULL CHECK (step IN ('BILLING', 'INVENTORY', 'DELIVERY')),
    state VARCHAR(30) NOT NULL CHECK (state IN ('STARTED', 'COMPLETED', 'COMPENSATED', 'COMPENSATION_FAILED')),
    error TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (order_id, step)
);
--rollback DROP TABLE order_saga_steps;
--rollback ALTER TABLE customer_orders DROP COLUMN courier_id;

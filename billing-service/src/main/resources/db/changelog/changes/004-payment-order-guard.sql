--liquibase formatted sql

--changeset otus:billing-payment-order-guard-004
-- Coordinates charge and refund requests, including refunds received before a
-- withdrawal row has been committed.
CREATE TABLE payment_orders (
    order_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    cancelled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_payment_orders_positive_ids CHECK (order_id > 0 AND user_id > 0)
);

--rollback DROP TABLE payment_orders;

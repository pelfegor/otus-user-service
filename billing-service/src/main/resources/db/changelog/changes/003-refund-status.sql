--liquibase formatted sql
--changeset otus:billing-refund-003
ALTER TABLE transactions DROP CONSTRAINT ck_transactions_status;
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_status CHECK (status IN ('SUCCESS', 'FAILED', 'REFUNDED'));
--rollback ALTER TABLE transactions DROP CONSTRAINT ck_transactions_status;
--rollback ALTER TABLE transactions ADD CONSTRAINT ck_transactions_status CHECK (status IN ('SUCCESS', 'FAILED'));

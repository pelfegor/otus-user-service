--liquibase formatted sql
--changeset otus:notifications-002
ALTER TABLE notifications ADD COLUMN recipient VARCHAR(255);
ALTER TABLE notifications ADD COLUMN amount NUMERIC(19, 2);
UPDATE notifications SET recipient = '', amount = 0.00;
ALTER TABLE notifications ALTER COLUMN recipient SET NOT NULL;
ALTER TABLE notifications ALTER COLUMN amount SET NOT NULL;

--rollback ALTER TABLE notifications DROP COLUMN amount;
--rollback ALTER TABLE notifications DROP COLUMN recipient;

ALTER TABLE payments
    ADD COLUMN pg_transaction_id VARCHAR(100) NULL AFTER failure_reason,
    ADD COLUMN provider           VARCHAR(32)  NULL AFTER pg_transaction_id;

CREATE INDEX idx_payments_pg_transaction_id ON payments (pg_transaction_id);

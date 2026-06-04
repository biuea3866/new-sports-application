-- DB-02 (결함#8): payments pg_transaction_id 동시 웹훅 중복 결제 완료 DB 방어선
-- 비-unique 인덱스를 제거하고 UNIQUE 인덱스로 재생성한다.
-- pg_transaction_id는 NULL 허용 → NULL 값은 유니크 제약 대상 제외 (MySQL 8.0 기본 동작).
DROP INDEX idx_payments_pg_transaction_id ON payments;

CREATE UNIQUE INDEX uq_payments_pg_transaction_id ON payments (pg_transaction_id);

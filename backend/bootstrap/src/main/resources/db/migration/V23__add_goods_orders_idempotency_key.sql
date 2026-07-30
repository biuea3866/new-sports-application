-- V23: goods_orders 테이블에 idempotency_key 컬럼 추가 (DEF-001 멱등 처리)

ALTER TABLE goods_orders
    ADD COLUMN idempotency_key VARCHAR(255) NULL AFTER user_id;

ALTER TABLE goods_orders
    ADD UNIQUE INDEX uq_goods_orders_idempotency_key (idempotency_key);

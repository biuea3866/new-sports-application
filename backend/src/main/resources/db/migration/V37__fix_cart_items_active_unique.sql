-- V37: DEF-001 fix — cart_item (cart_id, product_id) 활성 단일성 보장 (carts V36 와 동일 패턴)
--
-- 문제: uq_cart_items_cart_product (cart_id, product_id, deleted_at) 에서
--       MySQL 은 NULL != NULL 으로 처리하므로 deleted_at IS NULL 인 활성 row 를
--       동일 (cart_id, product_id) 로 여러 건 삽입할 수 있다.
--       결과: 동시 추가 시 활성 cart_item 이 2건 → findByCartIdAndProductIdAndDeletedAtIsNull
--       이 2건 반환 → IncorrectResultSizeDataAccessException 500.
--
-- 해결:
--   1. 기존 중복(cart_id+product_id 별 활성 row > 1건) 을 soft-delete 로 정리한다 (최신 id 1건 유지).
--   2. active_marker 컬럼 추가: 활성 item = 1(상수), soft-delete = NULL(UNIQUE 체크 제외).
--   3. UNIQUE(cart_id, product_id, active_marker) 로 활성 중복 생성 원천 차단.
--
-- 실행 순서: 중복 정리 → 컬럼 추가 → active_marker 초기화 → 제약 추가.

-- Step 1: 중복 활성 row 정리 — (cart_id, product_id) 별로 가장 큰 id(최신) 1건을 제외한 나머지 soft-delete
UPDATE cart_items ci
    INNER JOIN (
        SELECT cart_id, product_id, MAX(id) AS keep_id
        FROM cart_items
        WHERE deleted_at IS NULL
        GROUP BY cart_id, product_id
        HAVING COUNT(*) > 1
    ) AS duplicates
        ON ci.cart_id = duplicates.cart_id
       AND ci.product_id = duplicates.product_id
SET ci.deleted_at = NOW(6),
    ci.deleted_by = NULL,
    ci.updated_at = NOW(6)
WHERE ci.deleted_at IS NULL
  AND ci.id != duplicates.keep_id;

-- Step 2: active_marker 컬럼 추가 (nullable — soft-delete 시 NULL)
ALTER TABLE cart_items
    ADD COLUMN active_marker BIGINT NULL DEFAULT NULL
        COMMENT 'UNIQUE 보조 컬럼. 활성 item: active_marker = 1(상수), soft-delete: NULL.';

-- Step 3: 기존 활성 row 의 active_marker = 1 로 초기화
UPDATE cart_items
SET active_marker = 1
WHERE deleted_at IS NULL;

-- Step 4: UNIQUE(cart_id, product_id, active_marker) 제약 추가
--         NULL 은 UNIQUE 체크 대상 아님 → soft-delete 된 row 는 중복 허용(같은 상품 재추가 가능).
ALTER TABLE cart_items
    ADD CONSTRAINT uq_cart_items_cart_product_active UNIQUE (cart_id, product_id, active_marker);

-- Step 5: active_marker 조회 최적화 인덱스
CREATE INDEX idx_cart_items_active_marker ON cart_items (active_marker);

-- Step 6: 낙관락 version 컬럼 (동시 addQuantity lost update 방지)
ALTER TABLE cart_items
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0
        COMMENT '@Version 낙관락 — 동시 수량 갱신 충돌 감지.';

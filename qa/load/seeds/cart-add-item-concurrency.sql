-- LOAD-04 사전 시드: cart 동시성 부하 테스트용
-- 목적: POST /cart/items 동시 요청 시 user당 활성 cart 1건 수렴 검증 (V34 active_marker 제약 회귀)
--
-- 상태 보장:
--   - products 1~10번: ACTIVE, stock 충분(9999건)
--   - users 1~5번: 활성 cart 0건 (deleted_at IS NULL 없음)
--   - 기존 활성 cart가 있으면 soft-delete로 정리 후 재시드

-- Step 1: user 1~5의 기존 활성 cart soft-delete (멱등 시드)
UPDATE carts
SET deleted_at = NOW(6),
    deleted_by = NULL,
    updated_at = NOW(6),
    active_marker = NULL
WHERE user_id IN (1, 2, 3, 4, 5)
  AND deleted_at IS NULL;

-- Step 2: products 1~10 ACTIVE + stock 충분 (insert or update)
-- owner_id = 1 (부하 테스트용 dummy 소유자)
INSERT INTO products (id, name, category, price, description, image_url, status, owner_id, created_at, created_by, updated_at, updated_by, deleted_at, deleted_by)
VALUES
    (1,  '부하테스트상품-01', 'EQUIPMENT', 10000.00, 'LOAD-04 시드', NULL, 'ACTIVE', 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (2,  '부하테스트상품-02', 'EQUIPMENT', 10000.00, 'LOAD-04 시드', NULL, 'ACTIVE', 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (3,  '부하테스트상품-03', 'EQUIPMENT', 10000.00, 'LOAD-04 시드', NULL, 'ACTIVE', 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (4,  '부하테스트상품-04', 'EQUIPMENT', 10000.00, 'LOAD-04 시드', NULL, 'ACTIVE', 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (5,  '부하테스트상품-05', 'EQUIPMENT', 10000.00, 'LOAD-04 시드', NULL, 'ACTIVE', 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (6,  '부하테스트상품-06', 'EQUIPMENT', 10000.00, 'LOAD-04 시드', NULL, 'ACTIVE', 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (7,  '부하테스트상품-07', 'EQUIPMENT', 10000.00, 'LOAD-04 시드', NULL, 'ACTIVE', 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (8,  '부하테스트상품-08', 'EQUIPMENT', 10000.00, 'LOAD-04 시드', NULL, 'ACTIVE', 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9,  '부하테스트상품-09', 'EQUIPMENT', 10000.00, 'LOAD-04 시드', NULL, 'ACTIVE', 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (10, '부하테스트상품-10', 'EQUIPMENT', 10000.00, 'LOAD-04 시드', NULL, 'ACTIVE', 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE
    status     = 'ACTIVE',
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = NOW(6);

-- Step 3: stocks 1~10 충분한 재고 (insert or update)
INSERT INTO stocks (product_id, quantity, version, created_at, created_by, updated_at, updated_by, deleted_at, deleted_by)
VALUES
    (1,  9999, 0, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (2,  9999, 0, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (3,  9999, 0, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (4,  9999, 0, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (5,  9999, 0, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (6,  9999, 0, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (7,  9999, 0, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (8,  9999, 0, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9,  9999, 0, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (10, 9999, 0, NOW(6), NULL, NOW(6), NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE
    quantity   = 9999,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = NOW(6);

-- 검증 쿼리 (시드 확인용)
-- SELECT COUNT(*) FROM products WHERE id BETWEEN 1 AND 10 AND status = 'ACTIVE';   -- 10
-- SELECT COUNT(*) FROM stocks WHERE product_id BETWEEN 1 AND 10 AND quantity >= 9999;  -- 10
-- SELECT COUNT(*) FROM carts WHERE user_id IN (1,2,3,4,5) AND deleted_at IS NULL;    -- 0

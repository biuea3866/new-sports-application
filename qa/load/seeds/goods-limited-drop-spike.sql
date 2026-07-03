-- LOAD-05 사전 시드: 한정판 마케팅 스파이크 부하 테스트용
-- 목적: goods-limited-drop-spike.js의 setup()이 POST /limited-drops로 회차를 개설할 때
--       참조할 판매 대상 product(+stock)를 미리 확보한다 (Stock.@Version 경합 대상 단일 행).
--
-- 상태 보장:
--   - product 9000001: ACTIVE, owner_id = 9000000, stock 5,000,000 (스파이크 트래픽 전량을
--     Stock.deduct() 실패 없이 흡수할 만큼 과분히 크게 잡아, "재고 부족(OutOfStockException)"이
--     아니라 "한정판 회차의 limitedQuantity 소진(LimitedDropSoldOutException, Redis 게이트)"만
--     정상 실패 경로로 관측되게 한다.

INSERT INTO products (id, name, category, price, description, image_url, status, owner_id, created_at, created_by, updated_at, updated_by, deleted_at, deleted_by)
VALUES
    (9000001, 'LOAD-05 부하테스트 한정판 상품', 'EQUIPMENT', 50000.00, 'LOAD-05 시드', NULL, 'ACTIVE', 9000000, NOW(6), NULL, NOW(6), NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE
    status     = 'ACTIVE',
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = NOW(6);

INSERT INTO stocks (product_id, quantity, version, created_at, created_by, updated_at, updated_by, deleted_at, deleted_by)
VALUES
    (9000001, 5000000, 0, NOW(6), NULL, NOW(6), NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE
    quantity   = 5000000,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = NOW(6);

-- 검증 쿼리 (시드 확인용)
-- SELECT COUNT(*) FROM products WHERE id = 9000001 AND status = 'ACTIVE';        -- 1
-- SELECT quantity FROM stocks WHERE product_id = 9000001;                        -- 5000000

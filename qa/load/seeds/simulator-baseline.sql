-- INFRA-03 사전 시드: 상시 트래픽 시뮬레이터 synthetic baseline
-- 근거 티켓: 프로젝트/스포츠앱/상시 트래픽 시뮬레이터/Tickets/INFRA-03-synthetic-provision-시드-발급.md
-- 근거 TDD: 프로젝트/스포츠앱/상시 트래픽 시뮬레이터/TDD.md "synthetic 격리 계약"
--
-- 목적: reseed(INFRA-08, 10분 주기)가 복원 기준으로 재사용할 synthetic 자원 baseline을
--       멱등 upsert한다. 이 파일은 provision.sh가 최초 실행 시 적용하고, reseed 배치가
--       동일 파일(또는 동일 절대값 upsert 로직)로 소진성 자원을 baseline으로 되돌린다.
--
-- synthetic 격리 범위 (TDD "synthetic 격리 계약" 표):
--   - 시설/슬롯: facility_id = 'SYN-FAC-1'..'SYN-FAC-3', slots.id = 9000001~9000030, owner_id = 1(더미)
--   - 상품/재고: products.id = 9010001~9010010, owner_id = 1(더미)
--     * goods-limited-drop-spike.sql(LOAD-05, 단발 회귀)이 이미 상품 id 9000001을
--       owner_id=9000000로 점유하고 있어(다른 소유자), 본 baseline은 그 id와 문자 그대로
--       겹치지 않도록 9010001+로 오프셋했다 — LimitedDropDomainService.createDrop이
--       productWithStock.requireOwnedBy(ownerUserId)로 소유자 일치를 강제하므로, 서로
--       다른 owner_id를 가진 두 시드가 같은 PK를 다른 값으로 매 실행마다 덮어쓰면
--       두 QA 자산이 서로의 상태를 깨뜨린다(Single Writer per row 원칙 위반 방지).
--   - 이벤트/좌석: events.id = 9000001(테이블이 달라 products.id와 PK 네임스페이스가
--     겹치지 않는다), seats.id = 9000001~9005000, owner_id = 1(더미)
--
-- 멱등성: 전부 INSERT ... ON DUPLICATE KEY UPDATE (PK 명시) — 반복 실행해도 행 수 불변,
--         값은 baseline 절대값으로 항상 수렴한다(드리프트 없음).
--
-- 롤백 (synthetic 범위 한정 delete — 실데이터 미접촉):
--   DELETE FROM seats    WHERE id BETWEEN 9000001 AND 9005000;
--   DELETE FROM events   WHERE id = 9000001;
--   DELETE FROM stocks   WHERE product_id BETWEEN 9010001 AND 9010010;
--   DELETE FROM products WHERE id BETWEEN 9010001 AND 9010010;
--   DELETE FROM bookings WHERE slot_id BETWEEN 9000001 AND 9000030;
--   DELETE FROM slots    WHERE id BETWEEN 9000001 AND 9000030;

-- ============================================================
-- Step 1: 시설·슬롯 baseline (booking 도메인)
--   facility_id 풀 3개 x 슬롯 10개(시간대 08:00~18:00) = 30 슬롯
--   date는 고정 미래 상수(2099-06-01)로 둬 실행 시각에 무관하게 매번 동일한 값으로 수렴시킨다
--   (오늘 날짜를 쓰면 날짜가 바뀔 때마다 값이 달라져 idempotent 검증이 실행일에 좌우된다).
--   capacity는 baseline 대형값(1,000,000) — 10분 reseed 주기 안에서 B2C 쓰기가 소진하지
--   못할 만큼 여유를 둔다(reseed는 이 값으로 복원만 하면 되고, 소모는 활성 예약 건수로 집계됨).
-- ============================================================
INSERT INTO slots (id, facility_id, date, time_range, capacity, owner_id, created_at, created_by, updated_at, updated_by, deleted_at, deleted_by)
VALUES
    (9000001, 'SYN-FAC-1', '2099-06-01 00:00:00.000000', '08:00-09:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000002, 'SYN-FAC-1', '2099-06-01 00:00:00.000000', '09:00-10:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000003, 'SYN-FAC-1', '2099-06-01 00:00:00.000000', '10:00-11:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000004, 'SYN-FAC-1', '2099-06-01 00:00:00.000000', '11:00-12:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000005, 'SYN-FAC-1', '2099-06-01 00:00:00.000000', '12:00-13:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000006, 'SYN-FAC-1', '2099-06-01 00:00:00.000000', '13:00-14:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000007, 'SYN-FAC-1', '2099-06-01 00:00:00.000000', '14:00-15:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000008, 'SYN-FAC-1', '2099-06-01 00:00:00.000000', '15:00-16:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000009, 'SYN-FAC-1', '2099-06-01 00:00:00.000000', '16:00-17:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000010, 'SYN-FAC-1', '2099-06-01 00:00:00.000000', '17:00-18:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000011, 'SYN-FAC-2', '2099-06-01 00:00:00.000000', '08:00-09:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000012, 'SYN-FAC-2', '2099-06-01 00:00:00.000000', '09:00-10:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000013, 'SYN-FAC-2', '2099-06-01 00:00:00.000000', '10:00-11:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000014, 'SYN-FAC-2', '2099-06-01 00:00:00.000000', '11:00-12:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000015, 'SYN-FAC-2', '2099-06-01 00:00:00.000000', '12:00-13:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000016, 'SYN-FAC-2', '2099-06-01 00:00:00.000000', '13:00-14:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000017, 'SYN-FAC-2', '2099-06-01 00:00:00.000000', '14:00-15:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000018, 'SYN-FAC-2', '2099-06-01 00:00:00.000000', '15:00-16:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000019, 'SYN-FAC-2', '2099-06-01 00:00:00.000000', '16:00-17:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000020, 'SYN-FAC-2', '2099-06-01 00:00:00.000000', '17:00-18:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000021, 'SYN-FAC-3', '2099-06-01 00:00:00.000000', '08:00-09:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000022, 'SYN-FAC-3', '2099-06-01 00:00:00.000000', '09:00-10:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000023, 'SYN-FAC-3', '2099-06-01 00:00:00.000000', '10:00-11:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000024, 'SYN-FAC-3', '2099-06-01 00:00:00.000000', '11:00-12:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000025, 'SYN-FAC-3', '2099-06-01 00:00:00.000000', '12:00-13:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000026, 'SYN-FAC-3', '2099-06-01 00:00:00.000000', '13:00-14:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000027, 'SYN-FAC-3', '2099-06-01 00:00:00.000000', '14:00-15:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000028, 'SYN-FAC-3', '2099-06-01 00:00:00.000000', '15:00-16:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000029, 'SYN-FAC-3', '2099-06-01 00:00:00.000000', '16:00-17:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9000030, 'SYN-FAC-3', '2099-06-01 00:00:00.000000', '17:00-18:00', 1000000, 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE
    facility_id = VALUES(facility_id),
    date        = VALUES(date),
    time_range  = VALUES(time_range),
    capacity    = 1000000,
    owner_id    = 1,
    deleted_at  = NULL,
    deleted_by  = NULL,
    updated_at  = NOW(6);

-- ============================================================
-- Step 2: 상품·재고 baseline (goods 도메인)
--   id 9010001~9010010 (10개), owner_id=1(더미), stock baseline 1,000,000
--   (B2C write 30% 배분이 10분 reseed 주기 안에서 재고를 소진해 병목이 되지 않을 만큼 과분히 크게 잡음)
-- ============================================================
INSERT INTO products (id, name, category, price, description, image_url, status, owner_id, created_at, created_by, updated_at, updated_by, deleted_at, deleted_by)
VALUES
    (9010001, 'SIM 상시부하-synthetic상품-01', 'EQUIPMENT', 10000.00, 'INFRA-03 상시 트래픽 시뮬레이터 baseline', NULL, 'ACTIVE', 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9010002, 'SIM 상시부하-synthetic상품-02', 'EQUIPMENT', 10000.00, 'INFRA-03 상시 트래픽 시뮬레이터 baseline', NULL, 'ACTIVE', 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9010003, 'SIM 상시부하-synthetic상품-03', 'EQUIPMENT', 10000.00, 'INFRA-03 상시 트래픽 시뮬레이터 baseline', NULL, 'ACTIVE', 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9010004, 'SIM 상시부하-synthetic상품-04', 'EQUIPMENT', 10000.00, 'INFRA-03 상시 트래픽 시뮬레이터 baseline', NULL, 'ACTIVE', 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9010005, 'SIM 상시부하-synthetic상품-05', 'EQUIPMENT', 10000.00, 'INFRA-03 상시 트래픽 시뮬레이터 baseline', NULL, 'ACTIVE', 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9010006, 'SIM 상시부하-synthetic상품-06', 'EQUIPMENT', 10000.00, 'INFRA-03 상시 트래픽 시뮬레이터 baseline', NULL, 'ACTIVE', 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9010007, 'SIM 상시부하-synthetic상품-07', 'EQUIPMENT', 10000.00, 'INFRA-03 상시 트래픽 시뮬레이터 baseline', NULL, 'ACTIVE', 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9010008, 'SIM 상시부하-synthetic상품-08', 'EQUIPMENT', 10000.00, 'INFRA-03 상시 트래픽 시뮬레이터 baseline', NULL, 'ACTIVE', 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9010009, 'SIM 상시부하-synthetic상품-09', 'EQUIPMENT', 10000.00, 'INFRA-03 상시 트래픽 시뮬레이터 baseline', NULL, 'ACTIVE', 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9010010, 'SIM 상시부하-synthetic상품-10', 'EQUIPMENT', 10000.00, 'INFRA-03 상시 트래픽 시뮬레이터 baseline', NULL, 'ACTIVE', 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE
    status     = 'ACTIVE',
    owner_id   = 1,
    price      = 10000.00,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = NOW(6);

INSERT INTO stocks (product_id, quantity, version, created_at, created_by, updated_at, updated_by, deleted_at, deleted_by)
VALUES
    (9010001, 1000000, 0, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9010002, 1000000, 0, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9010003, 1000000, 0, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9010004, 1000000, 0, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9010005, 1000000, 0, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9010006, 1000000, 0, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9010007, 1000000, 0, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9010008, 1000000, 0, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9010009, 1000000, 0, NOW(6), NULL, NOW(6), NULL, NULL, NULL),
    (9010010, 1000000, 0, NOW(6), NULL, NOW(6), NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE
    quantity   = 1000000,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = NOW(6);

-- ============================================================
-- Step 3: 이벤트·좌석 baseline (ticketing 도메인)
--   event id = 9000001(단일 전용 이벤트, status=OPEN), 좌석 5000석
--   (섹션 10개 x 열 10개 x 좌석번호 50개 = 5000 — 좌석 잠금(Redis TTL 300s) 부하가
--   짧은 시간 안에 좌석 풀을 소진하지 않도록 ticket-seat-select-spike.js가 참조하는
--   기존 SEAT_POOL_SIZE=5000 관례와 동일 규모로 맞춘다)
-- ============================================================
INSERT INTO events (id, title, venue, starts_at, status, owner_id, created_at, created_by, updated_at, updated_by, deleted_at, deleted_by)
VALUES
    (9000001, 'INFRA-03 상시 트래픽 시뮬레이터 synthetic 이벤트', 'SYN-VENUE-1', '2099-06-01 19:00:00.000000', 'OPEN', 1, NOW(6), NULL, NOW(6), NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE
    status     = 'OPEN',
    owner_id   = 1,
    starts_at  = VALUES(starts_at),
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = NOW(6);

-- 좌석 5000석 생성 — 숫자 생성기(0~9 자릿수 x4 CROSS JOIN, 재귀 CTE 깊이 제한(cte_max_recursion_depth)에
-- 의존하지 않는 표준 "numbers table" 패턴)로 n=0..4999를 만들고 섹션(10) x 열(10) x 좌석번호(50)로 매핑한다.
INSERT INTO seats (id, event_id, section, row_no, seat_no, price, created_at, created_by, updated_at, updated_by, deleted_at, deleted_by)
SELECT
    9000001 + n                                              AS id,
    9000001                                                  AS event_id,
    CONCAT('SEC-', LPAD(FLOOR(n / 500) + 1, 2, '0'))          AS section,
    LPAD(FLOOR(MOD(n, 500) / 50) + 1, 2, '0')                 AS row_no,
    LPAD(MOD(n, 50) + 1, 2, '0')                              AS seat_no,
    50000.00                                                  AS price,
    NOW(6) AS created_at, NULL AS created_by, NOW(6) AS updated_at, NULL AS updated_by, NULL AS deleted_at, NULL AS deleted_by
FROM (
    SELECT (thousands.d * 1000 + hundreds.d * 100 + tens.d * 10 + ones.d) AS n
    FROM
        (SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) AS thousands
        CROSS JOIN
        (SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) AS hundreds
        CROSS JOIN
        (SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) AS tens
        CROSS JOIN
        (SELECT 0 AS d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) AS ones
) AS number_sequence
WHERE n BETWEEN 0 AND 4999
ON DUPLICATE KEY UPDATE
    price      = 50000.00,
    deleted_at = NULL,
    deleted_by = NULL,
    updated_at = NOW(6);

-- 검증 쿼리 (시드 확인용)
-- SELECT COUNT(*) FROM slots WHERE id BETWEEN 9000001 AND 9000030 AND capacity = 1000000 AND deleted_at IS NULL;  -- 30
-- SELECT COUNT(DISTINCT facility_id) FROM slots WHERE id BETWEEN 9000001 AND 9000030;                            -- 3
-- SELECT COUNT(*) FROM products WHERE id BETWEEN 9010001 AND 9010010 AND status = 'ACTIVE' AND owner_id = 1;     -- 10
-- SELECT COUNT(*) FROM stocks WHERE product_id BETWEEN 9010001 AND 9010010 AND quantity = 1000000;               -- 10
-- SELECT COUNT(*) FROM events WHERE id = 9000001 AND status = 'OPEN' AND owner_id = 1;                           -- 1
-- SELECT COUNT(*) FROM seats WHERE id BETWEEN 9000001 AND 9005000 AND event_id = 9000001;                        -- 5000

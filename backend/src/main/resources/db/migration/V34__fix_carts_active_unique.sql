-- V34: DEF-002 fix — cart user당 활성 row 단일성 보장
--
-- 문제: UNIQUE KEY uq_carts_user_id (user_id, deleted_at) 에서
--       MySQL 은 NULL != NULL 으로 처리하므로 deleted_at IS NULL 인 row 를
--       동일 user_id 로 여러 건 삽입할 수 있다.
--       결과: findByUserIdAndDeletedAtIsNull 이 2건 이상 반환 → NonUniqueResultException 500.
--
-- 해결:
--   1. 기존 중복(userId별 활성 row > 1건) 을 soft-delete 로 정리한다 (최신 id 1건 유지).
--   2. active_marker 컬럼 추가:
--      - 활성 cart: active_marker = id (null 이 아닌 고유값 → UNIQUE 적용)
--      - soft-delete 된 cart: active_marker = NULL (null 은 UNIQUE 체크 제외)
--   3. UNIQUE(user_id, active_marker) 제약으로 활성 cart 중복 생성 원천 차단.
--
-- 실행 순서: 중복 정리 → 컬럼 추가 → active_marker 초기화 → 제약 추가.

-- Step 1: 중복 활성 row 정리 — userId 별로 가장 큰 id(최신) 1건을 제외한 나머지 soft-delete
UPDATE carts c
    INNER JOIN (
        SELECT user_id, MAX(id) AS keep_id
        FROM carts
        WHERE deleted_at IS NULL
        GROUP BY user_id
        HAVING COUNT(*) > 1
    ) AS duplicates ON c.user_id = duplicates.user_id
SET c.deleted_at = NOW(6),
    c.deleted_by = NULL,
    c.updated_at = NOW(6)
WHERE c.deleted_at IS NULL
  AND c.id != duplicates.keep_id;

-- Step 2: active_marker 컬럼 추가 (nullable — soft-delete 시 NULL)
ALTER TABLE carts
    ADD COLUMN active_marker BIGINT NULL DEFAULT NULL
        COMMENT 'UNIQUE 보조 컬럼. 활성 cart: active_marker = 1(상수), soft-delete: NULL.';

-- Step 3: 기존 활성 row 의 active_marker = 1 로 초기화
UPDATE carts
SET active_marker = 1
WHERE deleted_at IS NULL;

-- Step 4: UNIQUE(user_id, active_marker) 제약 추가
--         NULL 은 UNIQUE 체크 대상 아님 → soft-delete 된 row 는 중복 허용.
ALTER TABLE carts
    ADD CONSTRAINT uq_carts_user_id_active_marker UNIQUE (user_id, active_marker);

-- Step 5: active_marker 조회 최적화 인덱스
CREATE INDEX idx_carts_active_marker ON carts (active_marker);

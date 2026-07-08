-- V58: 모집·시설상품·소모임예약 연동 S3 — posts 인덱스 3종 (A-Q1/A-Q2/A-Q3)
-- 설계 근거: /Users/biuea/Desktop/dpdpdndn/프로젝트/스포츠앱/모임·커뮤니티/20260707-모집커뮤니티연동-design-db.md
--   "쿼리 패턴 → 인덱스 매핑 — PRD A posts"
-- 대상 쿼리:
--   A-Q1 전역 피드: deleted_at IS NULL AND global_listed=1 ORDER BY created_at DESC (최빈 hot path)
--   A-Q2 모임 게시글 목록: community_id=? AND deleted_at IS NULL [AND sport_category=?] ORDER BY created_at DESC
--   A-Q3 종목별 전역 피드: deleted_at IS NULL AND global_listed=1 AND sport_category=? ORDER BY created_at DESC
-- 락 영향: 전부 ALGORITHM=INPLACE, LOCK=NONE 명시 — 온라인 DDL, 쓰기 블로킹 없음.
-- 배포 순서: 스키마 먼저(본 마이그레이션, V57 posts 컬럼 추가 이후) → 코드(플래그 OFF) → 플래그 점진 ON.
-- 롤백(역방향 DDL): 아래를 그대로 실행한다.
--   ALTER TABLE posts DROP INDEX idx_posts_global_listed_deleted_at_created_at, ALGORITHM=INPLACE, LOCK=NONE;
--   ALTER TABLE posts DROP INDEX idx_posts_community_id_deleted_at_created_at, ALGORITHM=INPLACE, LOCK=NONE;
--   ALTER TABLE posts DROP INDEX idx_posts_sport_category_global_listed_deleted_at_created_at, ALGORITHM=INPLACE, LOCK=NONE;

-- A-Q1: global_listed=1 equality 고정 → deleted_at IS NULL equality → created_at 정렬. filesort 제거 목적
ALTER TABLE posts
    ADD INDEX idx_posts_global_listed_deleted_at_created_at (global_listed, deleted_at, created_at),
    ALGORITHM=INPLACE, LOCK=NONE;

-- A-Q2: community_id 고카디널리티 equality 선두 → deleted_at → created_at 정렬
ALTER TABLE posts
    ADD INDEX idx_posts_community_id_deleted_at_created_at (community_id, deleted_at, created_at),
    ALGORITHM=INPLACE, LOCK=NONE;

-- A-Q3: sport_category(선택도 높음) equality 선두 → global_listed equality → deleted_at → created_at 정렬
ALTER TABLE posts
    ADD INDEX idx_posts_sport_category_global_listed_deleted_at_created_at (sport_category, global_listed, deleted_at, created_at),
    ALGORITHM=INPLACE, LOCK=NONE;

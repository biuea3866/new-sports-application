-- V57: 모집·시설상품·소모임예약 연동 S2 — posts 컬럼 3개 추가 (post↔community 연동)
-- 설계 근거: /Users/biuea/Desktop/dpdpdndn/프로젝트/스포츠앱/모임·커뮤니티/20260707-모집커뮤니티연동-design-db.md
--   "MySQL 테이블 정의 5. posts 컬럼 추가"
-- 하위 호환 판단:
--   community_id(nullable) — 컬럼 추가(nullable) → 단일 마이그레이션. NULL=전역 게시글(기존 동작 불변)
--   sport_category(nullable) — 컬럼 추가(nullable) → 단일 마이그레이션. NULL=미지정
--   global_listed(NOT NULL DEFAULT 1) — DEFAULT 1이 기존 행 100%에 유효한 정상값(전역=노출)이라
--     3단계 분리 불요. private-db-schema-convention의 "채우기" 단계가 DEFAULT로 원자 충족.
-- 락 영향: MySQL 8.0 INSTANT ADD COLUMN(DEFAULT 포함) — 무락. 기존 posts 조회·쓰기 영향 없음.
-- 배포 순서: 스키마 먼저(본 마이그레이션) → 코드(플래그 OFF) → 플래그 점진 ON.
-- 롤백(역방향 DDL): 아래를 그대로 실행한다.
--   ALTER TABLE posts DROP COLUMN community_id;
--   ALTER TABLE posts DROP COLUMN sport_category;
--   ALTER TABLE posts DROP COLUMN global_listed;

ALTER TABLE posts
    ADD COLUMN community_id BIGINT NULL COMMENT '소속 community id 논리 참조. NULL=전역 게시글. FK 금지' AFTER content,
    ADD COLUMN sport_category VARCHAR(30) NULL COMMENT '스포츠 종목 카테고리. communities.sport_category와 동일 타입. NULL=미지정. ENUM 금지 — VARCHAR' AFTER community_id,
    ADD COLUMN global_listed TINYINT(1) NOT NULL DEFAULT 1 COMMENT '전역 피드 노출 여부. BOOLEAN 금지 — TINYINT(1). DEFAULT 1=기존 전역 게시글 100% 유지' AFTER sport_category;

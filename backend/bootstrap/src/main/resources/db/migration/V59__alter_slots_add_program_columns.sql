-- V59: 모집·시설상품·소모임예약 연동 S4 — slots 컬럼 2개 추가 (program_id, status)
-- 설계 근거: /Users/biuea/Desktop/dpdpdndn/프로젝트/스포츠앱/모임·커뮤니티/20260707-모집커뮤니티연동-design-db.md
--   "MySQL 테이블 정의 6. slots 컬럼 추가", "slots UNIQUE 호환 확인"
-- 하위 호환 판단:
--   program_id(nullable) — 컬럼 추가(nullable) → 단일 마이그레이션. NULL=일반 슬롯, non-null=program 회차
--   status(nullable DEFAULT 'OPEN') — 과제 SSOT 요구대로 컬럼 자체는 nullable DEFAULT 'OPEN'.
--     DEFAULT가 기존 행 100%를 'OPEN'으로 채워 NULL이 실제로 발생하지 않으므로, 도메인 Entity(Slot.kt)는
--     status 컬럼을 nullable=false로 매핑한다(NULL 방어 로직 없음 — DEFAULT가 유일한 보장 수단).
-- UNIQUE 호환: 기존 UNIQUE uq_slots_facility_date_time_range(facility_id, date, time_range, deleted_at)는
--   변경하지 않는다. program_id/status는 이 UNIQUE 키 구성에 없으므로 자동 슬롯 멱등 재생성 로직
--   (facility_id, date, time_range 기준 diff 계산)에 영향 없음. program 세션 슬롯도 같은 UNIQUE
--   네임스페이스를 공유하므로 일반 슬롯과 동일 (facility_id, date, time_range) 조합은 사용할 수 없다
--   (Open Question — 다중 룸/코트 개념 도입 시 재설계 필요, 현재는 문서화된 제약으로 유지).
-- 락 영향: MySQL 8.0 INSTANT ADD COLUMN(DEFAULT 포함) — 무락. 기존 slots 조회·쓰기 영향 없음.
-- 배포 순서: 스키마 먼저(본 마이그레이션) → 코드(플래그 OFF: facility.autoslot.enabled/facility.program.enabled)
--   → 플래그 점진 ON. autoslot은 초기 OFF 필수(대량 슬롯 생성 사고 방지).
-- 롤백(역방향 DDL): 아래를 그대로 실행한다.
--   ALTER TABLE slots DROP COLUMN program_id;
--   ALTER TABLE slots DROP COLUMN status;

ALTER TABLE slots
    ADD COLUMN program_id BIGINT NULL COMMENT 'programs.id 논리 참조. NULL=일반 슬롯, non-null=program 회차. FK 금지' AFTER owner_id,
    ADD COLUMN status VARCHAR(20) NULL DEFAULT 'OPEN' COMMENT '슬롯 상태. DEFAULT OPEN이 기존 행을 채움(NULL 미발생). ENUM 금지 — VARCHAR' AFTER program_id;

-- V60: 모집·시설상품·소모임예약 연동 S5 — slots 인덱스 추가 (B-Q6)
-- 설계 근거: /Users/biuea/Desktop/dpdpdndn/프로젝트/스포츠앱/모임·커뮤니티/20260707-모집커뮤니티연동-design-db.md
--   "쿼리 패턴 → 인덱스 매핑 — PRD B B-Q6"
-- 대상 쿼리: program_id=? AND deleted_at IS NULL ORDER BY date (program 회차 목록 조회)
-- 컬럼 순서 근거: program_id equality 선두 → date 정렬 → deleted_at
-- 락 영향: ALGORITHM=INPLACE, LOCK=NONE 명시 — 온라인 DDL, 쓰기 블로킹 없음.
-- 배포 순서: 스키마 먼저(본 마이그레이션, V59 slots 컬럼 추가 이후) → 코드(플래그 OFF) → 플래그 점진 ON.
-- 롤백(역방향 DDL): 아래를 그대로 실행한다.
--   ALTER TABLE slots DROP INDEX idx_slots_program_id_date_deleted_at, ALGORITHM=INPLACE, LOCK=NONE;

ALTER TABLE slots
    ADD INDEX idx_slots_program_id_date_deleted_at (program_id, date, deleted_at),
    ALGORITHM=INPLACE, LOCK=NONE;

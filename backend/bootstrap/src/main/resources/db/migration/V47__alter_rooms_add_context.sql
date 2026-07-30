-- V47: 채팅 시스템 고도화 S1 — rooms에 context_type/context_id 추가 (additive)
-- 설계 근거: /Users/biuea/Desktop/dpdpdndn/프로젝트/채팅 시스템/20260704-채팅시스템고도화-design-db.md
--   "Detail Design > 1. rooms (기존 확장 — additive)", 쿼리 매핑 R1
-- 번호 재조정: design-db.md는 V38 전제였으나 origin/dev에 V38~V41(partner/limited_drops)이 선점되어 V42로 시프트했다.
--   이후 V42/V43이 #206(alerts)·#208(regions)과 병렬 머지로 중복되어 V47~V51로 재시프트.
-- 락 영향: ADD COLUMN(nullable, DEFAULT 없음) = MySQL 8.0 INSTANT 가능(락 없음). 인덱스 추가는 ALGORITHM=INPLACE, LOCK=NONE 명시.
-- 롤백(역방향 DDL):
--   ALTER TABLE rooms DROP INDEX idx_rooms_context;
--   ALTER TABLE rooms DROP COLUMN context_type, DROP COLUMN context_id;

ALTER TABLE rooms
    ADD COLUMN context_type VARCHAR(30) NULL COMMENT '연결된 외부 도메인 유형 (COMMUNITY / GOODS_PRODUCT). DIRECT/GROUP 순수 방은 NULL' AFTER last_message_at,
    ADD COLUMN context_id BIGINT NULL COMMENT '연결된 외부 엔티티 id (community_id 또는 product_id). context_type이 NULL이면 NULL. FK 금지 — 일반 BIGINT' AFTER context_type;

ALTER TABLE rooms
    ADD INDEX idx_rooms_context (context_type, context_id, deleted_at),
    ALGORITHM=INPLACE, LOCK=NONE;

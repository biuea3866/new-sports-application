-- V25: tickets.ticket_order_id NULL 전환 + sentinel 0L NULL backfill
-- [T06] v1.1 FR-04 — issueComplimentary() 팩토리 sentinel 제거
-- 선행 조건: V24 (mcp Phase 2 permissions)
--
-- [단계 0 사전 검증 — PR 본문 첨부 필수]
-- 운영 DB 적용 전 아래 SELECT를 실행하여 zero_count를 확인한다.
-- zero_count = issueComplimentary() 호출 누적 카운트와 일치 여부 확인 후 진행.
--
--   SELECT COUNT(*)                    AS zero_count,
--          MIN(created_at)             AS oldest,
--          MAX(created_at)             AS newest,
--          COUNT(DISTINCT seat_id)     AS distinct_seats
--   FROM   tickets
--   WHERE  ticket_order_id = 0;
--
-- expected 산정 방법:
--   mcp_audit_logs WHERE tool_name = 'issueComplimentaryTicket' AND status_code = 200 의 COUNT(*)
--   ※ T07 (Ticket nullable 전환) + v1.1 Complimentary 발급 코드 머지 이후에 비교 의미 발생.
--      v1.1 코드 미배포 상태에서 zero_count > 0 이면 의도되지 않은 데이터 가능성 — BE+DBA 추가 분석 필수.
--
-- [롤백 — 단계 2 코드 배포 전에만 가능. 상세 절차는 docs/migration/V25-rollback.md 참조]
-- UPDATE tickets SET ticket_order_id = 0 WHERE ticket_order_id IS NULL;
-- ALTER TABLE tickets MODIFY COLUMN ticket_order_id BIGINT NOT NULL COMMENT '티켓 주문 ID (NULL = 무료 증정 티켓)';
--
-- ※ 단계 2 배포 후 발생한 NULL row 는 0L sentinel 로 되돌릴 수 없습니다 (sentinel 재생성).
--    soft-delete 또는 별도 정책 결정 필요 — 단계 2 배포 후에는 forward-only 로 간주.

-- 1. 컬럼 NOT NULL → NULL 전환
--    COMMENT: NULL = 무료 증정(complimentary) 티켓, NOT NULL = 정상 주문 티켓
ALTER TABLE tickets
    MODIFY COLUMN ticket_order_id BIGINT NULL
        COMMENT '티켓 주문 ID. NULL이면 무료 증정(complimentary) 티켓';

-- 2. sentinel 0L 일괄 NULL backfill
--    issueComplimentary() 팩토리가 NOT NULL 회피를 위해 기록한 0L sentinel을 NULL로 정정한다.
UPDATE tickets
SET ticket_order_id = NULL
WHERE ticket_order_id = 0;

-- [검증]
-- SELECT COUNT(*) FROM tickets WHERE ticket_order_id IS NULL;
-- → 단계 0에서 확인한 zero_count와 일치해야 한다.

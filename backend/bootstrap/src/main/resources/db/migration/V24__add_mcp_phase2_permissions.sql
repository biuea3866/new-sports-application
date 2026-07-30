-- V24: MCP Phase 2 permission row 추가
-- [BE-03+16] Phase 2 Write tool — refundBooking / issueComplimentaryTicket
-- 선행 조건: V23 (mcp.* permissions 16건)
-- 멱등성: WHERE NOT EXISTS 로 중복 삽입 방지

INSERT INTO permissions (name, created_at, updated_at)
SELECT 'mcp.booking.write.refund', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'mcp.booking.write.refund');

INSERT INTO permissions (name, created_at, updated_at)
SELECT 'mcp.ticket.write.complimentary', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'mcp.ticket.write.complimentary');

-- 검증: SELECT COUNT(*) FROM permissions WHERE name LIKE 'mcp.%'; -- 18 반환 확인

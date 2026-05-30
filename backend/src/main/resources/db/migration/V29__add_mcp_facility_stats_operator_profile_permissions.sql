-- V23: MCP permission row 누락 보완 — mcp.facility.read.stats / mcp.operator.read.profile
-- [BE-13] pr-reviewer p1 fix
-- 선행 조건: V22 (mcp.* permissions 14건)
-- 멱등성: WHERE NOT EXISTS 로 중복 삽입 방지

INSERT INTO permissions (name, created_at, updated_at)
SELECT 'mcp.facility.read.stats', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'mcp.facility.read.stats');

INSERT INTO permissions (name, created_at, updated_at)
SELECT 'mcp.operator.read.profile', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'mcp.operator.read.profile');

-- 검증: SELECT COUNT(*) FROM permissions WHERE name LIKE 'mcp.%'; -- 16 반환 확인

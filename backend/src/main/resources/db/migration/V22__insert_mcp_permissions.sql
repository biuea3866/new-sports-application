-- V22: MCP 서버 — permissions 테이블에 MCP scope 14건 시드 삽입
-- [DB-01] B2B MCP 서버 MVP
-- 선행 조건: V2 (permissions 테이블), V20 (mcp_tokens)
-- 멱등성: WHERE NOT EXISTS 로 중복 삽입 방지
-- 롤백: DELETE FROM permissions WHERE name LIKE 'mcp.%';

-- facility scope (3건)
INSERT INTO permissions (name, created_at, updated_at)
SELECT 'mcp.facility.read.own', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'mcp.facility.read.own');

INSERT INTO permissions (name, created_at, updated_at)
SELECT 'mcp.facility.read.any', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'mcp.facility.read.any');

INSERT INTO permissions (name, created_at, updated_at)
SELECT 'mcp.facility.write.own', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'mcp.facility.write.own');

-- booking scope (3건)
INSERT INTO permissions (name, created_at, updated_at)
SELECT 'mcp.booking.read.own', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'mcp.booking.read.own');

INSERT INTO permissions (name, created_at, updated_at)
SELECT 'mcp.booking.read.any', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'mcp.booking.read.any');

INSERT INTO permissions (name, created_at, updated_at)
SELECT 'mcp.booking.write.own', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'mcp.booking.write.own');

-- goods scope (3건)
INSERT INTO permissions (name, created_at, updated_at)
SELECT 'mcp.goods.read.own', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'mcp.goods.read.own');

INSERT INTO permissions (name, created_at, updated_at)
SELECT 'mcp.goods.read.any', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'mcp.goods.read.any');

INSERT INTO permissions (name, created_at, updated_at)
SELECT 'mcp.goods.write.own', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'mcp.goods.write.own');

-- ticketing scope (3건)
INSERT INTO permissions (name, created_at, updated_at)
SELECT 'mcp.ticketing.read.own', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'mcp.ticketing.read.own');

INSERT INTO permissions (name, created_at, updated_at)
SELECT 'mcp.ticketing.read.any', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'mcp.ticketing.read.any');

INSERT INTO permissions (name, created_at, updated_at)
SELECT 'mcp.ticketing.write.own', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'mcp.ticketing.write.own');

-- notification scope (1건)
INSERT INTO permissions (name, created_at, updated_at)
SELECT 'mcp.notification.read.own', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'mcp.notification.read.own');

-- pii scope (1건, Phase 1 발급 보류 — permissions row만 삽입, 토큰 부여는 Phase 2 자격 검증 후)
INSERT INTO permissions (name, created_at, updated_at)
SELECT 'mcp.pii.unmask', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'mcp.pii.unmask');

-- 검증: SELECT COUNT(*) FROM permissions WHERE name LIKE 'mcp.%'; — 14 반환 확인

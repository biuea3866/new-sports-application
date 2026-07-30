-- V17: B2B Portal — events/products owner_id 컬럼 + B2B Role/Permission 시드
-- [B2B-01]
-- 사전 조건: events/products 테이블에 기존 row 0건 (신규 프로젝트 환경 가정)

-- 1. events 테이블에 owner_id 컬럼 추가
ALTER TABLE events
    ADD COLUMN owner_id BIGINT NOT NULL DEFAULT 0;

ALTER TABLE events
    MODIFY COLUMN owner_id BIGINT NOT NULL;

CREATE INDEX idx_events_owner_id ON events (owner_id);

-- 2. products 테이블에 owner_id 컬럼 추가
ALTER TABLE products
    ADD COLUMN owner_id BIGINT NOT NULL DEFAULT 0;

ALTER TABLE products
    MODIFY COLUMN owner_id BIGINT NOT NULL;

CREATE INDEX idx_products_owner_id ON products (owner_id);

-- 3. roles 시드 — EVENT_HOST, GOODS_SELLER (idempotent)
INSERT INTO roles (name, created_at, updated_at)
SELECT 'EVENT_HOST', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'EVENT_HOST');

INSERT INTO roles (name, created_at, updated_at)
SELECT 'GOODS_SELLER', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'GOODS_SELLER');

-- 4. permissions 시드 — 7건 (idempotent)
INSERT INTO permissions (name, created_at, updated_at)
SELECT 'facility:write', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'facility:write');

INSERT INTO permissions (name, created_at, updated_at)
SELECT 'facility:read', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'facility:read');

INSERT INTO permissions (name, created_at, updated_at)
SELECT 'event:write', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'event:write');

INSERT INTO permissions (name, created_at, updated_at)
SELECT 'event:read', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'event:read');

INSERT INTO permissions (name, created_at, updated_at)
SELECT 'product:write', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'product:write');

INSERT INTO permissions (name, created_at, updated_at)
SELECT 'product:read', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'product:read');

INSERT INTO permissions (name, created_at, updated_at)
SELECT 'b2b:dashboard:read', NOW(6), NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE name = 'b2b:dashboard:read');

-- 5. role_permissions 매핑 시드 (idempotent)

-- FACILITY_OWNER → facility:write, facility:read, b2b:dashboard:read
INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW(6), NOW(6)
FROM roles r
         JOIN permissions p ON p.name = 'facility:write'
WHERE r.name = 'FACILITY_OWNER'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.deleted_at IS NULL
);

INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW(6), NOW(6)
FROM roles r
         JOIN permissions p ON p.name = 'facility:read'
WHERE r.name = 'FACILITY_OWNER'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.deleted_at IS NULL
);

INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW(6), NOW(6)
FROM roles r
         JOIN permissions p ON p.name = 'b2b:dashboard:read'
WHERE r.name = 'FACILITY_OWNER'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.deleted_at IS NULL
);

-- EVENT_HOST → event:write, event:read, b2b:dashboard:read
INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW(6), NOW(6)
FROM roles r
         JOIN permissions p ON p.name = 'event:write'
WHERE r.name = 'EVENT_HOST'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.deleted_at IS NULL
);

INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW(6), NOW(6)
FROM roles r
         JOIN permissions p ON p.name = 'event:read'
WHERE r.name = 'EVENT_HOST'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.deleted_at IS NULL
);

INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW(6), NOW(6)
FROM roles r
         JOIN permissions p ON p.name = 'b2b:dashboard:read'
WHERE r.name = 'EVENT_HOST'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.deleted_at IS NULL
);

-- GOODS_SELLER → product:write, product:read, b2b:dashboard:read
INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW(6), NOW(6)
FROM roles r
         JOIN permissions p ON p.name = 'product:write'
WHERE r.name = 'GOODS_SELLER'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.deleted_at IS NULL
);

INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW(6), NOW(6)
FROM roles r
         JOIN permissions p ON p.name = 'product:read'
WHERE r.name = 'GOODS_SELLER'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.deleted_at IS NULL
);

INSERT INTO role_permissions (role_id, permission_id, created_at, updated_at)
SELECT r.id, p.id, NOW(6), NOW(6)
FROM roles r
         JOIN permissions p ON p.name = 'b2b:dashboard:read'
WHERE r.name = 'GOODS_SELLER'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.deleted_at IS NULL
);

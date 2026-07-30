-- V10: posts 테이블에 type 컬럼 추가 — POST-03

ALTER TABLE posts ADD COLUMN type VARCHAR(32) NOT NULL DEFAULT 'FREE' AFTER content;

CREATE INDEX idx_posts_type_deleted_at ON posts (type, deleted_at);

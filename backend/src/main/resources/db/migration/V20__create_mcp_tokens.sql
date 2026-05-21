-- V20: MCP 서버 — mcp_tokens + mcp_token_scopes 테이블 생성
-- [DB-01] B2B MCP 서버 MVP
-- 선행 조건: V19 (permissions 테이블 존재)
-- 롤백: DROP TABLE mcp_token_scopes; DROP TABLE mcp_tokens;

-- 1. mcp_tokens: 운영자별 MCP 인증 토큰 (장기 수명 bearer, bcrypt 해시 저장)
CREATE TABLE mcp_tokens (
    id                 BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'PK',
    user_id            BIGINT       NOT NULL                 COMMENT '토큰 소유자 user_id (users.id 참조)',
    name               VARCHAR(255) NOT NULL                 COMMENT '토큰 식별 이름 (운영자 입력)',
    token_hash         VARCHAR(255) NOT NULL                 COMMENT 'bcrypt 해시된 토큰 값 (평문은 1회만 노출 후 폐기)',
    status             VARCHAR(32)  NOT NULL                 COMMENT '토큰 상태: ACTIVE | SUSPENDED | REVOKED',
    non_interactive    TINYINT(1)   NOT NULL DEFAULT 0       COMMENT '비대화형 모드 여부 (cron/n8n 자동화 전용, 1=비대화형)',
    pii_unmask_granted TINYINT(1)   NOT NULL DEFAULT 0       COMMENT 'PII 평문 노출 권한 (Phase 1 항상 0, Phase 2 자격 검증 후 활성)',
    expires_at         DATETIME(6)  NULL                     COMMENT '토큰 만료 시각 (NULL=무기한, ADR-005)',
    last_used_at       DATETIME(6)  NULL                     COMMENT '마지막 tool 호출 시각',
    version            BIGINT       NOT NULL DEFAULT 0       COMMENT '낙관락(@Version) — 동시 suspend/revoke race lost-update 방지',
    created_at         DATETIME(6)  NOT NULL                 COMMENT '생성 시각 (UTC)',
    created_by         BIGINT       NULL                     COMMENT '생성자 user_id',
    updated_at         DATETIME(6)  NOT NULL                 COMMENT '마지막 수정 시각 (UTC)',
    updated_by         BIGINT       NULL                     COMMENT '마지막 수정자 user_id',
    deleted_at         DATETIME(6)  NULL                     COMMENT '소프트 삭제 시각 (NULL=활성)',
    deleted_by         BIGINT       NULL                     COMMENT '삭제자 user_id',
    PRIMARY KEY (id),
    -- token_hash 는 deleted_at 무관하게 전역 고유 (폐기 후에도 해시 재사용 금지)
    UNIQUE KEY uq_mcp_tokens_token_hash (token_hash),
    INDEX idx_mcp_tokens_user_id_deleted_at (user_id, deleted_at)  COMMENT '사용자별 활성 토큰 목록 조회',
    INDEX idx_mcp_tokens_status_deleted_at  (status, deleted_at)   COMMENT '상태별 필터 조회',
    INDEX idx_mcp_tokens_expires_at         (expires_at)           COMMENT '만료 스캐너 (ADR-005 만료 정책)'
) COMMENT='MCP 운영자 인증 토큰';

-- 2. mcp_token_scopes: 토큰-Permission 매핑 (1급 Entity, @ManyToMany 금지 원칙 준수)
CREATE TABLE mcp_token_scopes (
    id            BIGINT      NOT NULL AUTO_INCREMENT  COMMENT 'PK',
    token_id      BIGINT      NOT NULL                 COMMENT 'mcp_tokens.id 참조',
    permission_id BIGINT      NOT NULL                 COMMENT 'permissions.id 참조',
    created_at    DATETIME(6) NOT NULL                 COMMENT '생성 시각 (UTC)',
    created_by    BIGINT      NULL                     COMMENT '생성자 user_id',
    updated_at    DATETIME(6) NOT NULL                 COMMENT '마지막 수정 시각 (UTC)',
    updated_by    BIGINT      NULL                     COMMENT '마지막 수정자 user_id',
    deleted_at    DATETIME(6) NULL                     COMMENT '소프트 삭제 시각 (NULL=활성)',
    deleted_by    BIGINT      NULL                     COMMENT '삭제자 user_id',
    PRIMARY KEY (id),
    -- soft-delete 포함 unique: 활성(deleted_at IS NULL) 중복 방지 + 폐기 후 재부여 허용
    UNIQUE KEY uq_mcp_token_scopes_token_perm_del (token_id, permission_id, deleted_at),
    INDEX idx_mcp_token_scopes_token_id   (token_id)   COMMENT '토큰별 scope 목록 조회',
    INDEX idx_mcp_token_scopes_deleted_at (deleted_at) COMMENT 'soft-delete 필터'
) COMMENT='MCP 토큰-Permission 매핑 (scope 부여 이력)';

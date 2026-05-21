-- V21: MCP 서버 — mcp_audit_logs 테이블 생성
-- [DB-01] B2B MCP 서버 MVP
-- append-only immutable 테이블: created_* 컬럼만 보유 (updated_*/deleted_* 없음)
-- 보관 정책: 90일 (hard-delete 스케줄러는 본 MVP 범위 외 별도 티켓)
-- 롤백: DROP TABLE mcp_audit_logs;

CREATE TABLE mcp_audit_logs (
    id                BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'PK',
    token_id          BIGINT       NULL                     COMMENT 'mcp_tokens.id 참조 (토큰 폐기 후에도 로그 보존을 위해 NULL 허용)',
    user_id           BIGINT       NOT NULL                 COMMENT 'tool 호출 운영자 user_id',
    tool_name         VARCHAR(100) NOT NULL                 COMMENT '호출된 MCP tool 이름 (예: getBookings, createSlot)',
    params_masked     TEXT         NULL                     COMMENT 'tool 호출 파라미터 직렬화 텍스트 (PII 마스킹 룰 적용 후 저장)',
    status_code       INT          NOT NULL                 COMMENT 'tool 응답 HTTP 상태 코드 (200/401/403/409/429 등)',
    latency_ms        INT          NOT NULL                 COMMENT 'tool 처리 소요 시간 (밀리초)',
    client_user_agent VARCHAR(500) NULL                     COMMENT 'MCP 클라이언트 User-Agent 문자열',
    ip_addr           VARCHAR(45)  NULL                     COMMENT '호출 클라이언트 IP 주소 (IPv4/IPv6)',
    asn               VARCHAR(100) NULL                     COMMENT 'IP의 ASN (자율시스템 번호, 비정상 패턴 탐지용)',
    called_at         DATETIME(6)  NOT NULL                 COMMENT 'tool 호출 시각 (UTC, 인덱스 기준 컬럼)',
    created_at        DATETIME(6)  NOT NULL                 COMMENT '레코드 생성 시각 (UTC, append-only)',
    created_by        BIGINT       NULL                     COMMENT '생성자 user_id (시스템 적재 시 NULL)',
    PRIMARY KEY (id),
    INDEX idx_mcp_audit_logs_user_id_called_at   (user_id, called_at)   COMMENT '운영자별 감사 로그 최신순 조회',
    INDEX idx_mcp_audit_logs_token_id_called_at  (token_id, called_at)  COMMENT '토큰별 감사 로그 최신순 조회',
    INDEX idx_mcp_audit_logs_tool_name_called_at (tool_name, called_at) COMMENT 'tool별 호출 통계 집계',
    INDEX idx_mcp_audit_logs_called_at           (called_at)            COMMENT '90일 보관 정책: 만료 row 범위 스캔'
) COMMENT='MCP tool 호출 감사 로그 (append-only, 90일 보관)';

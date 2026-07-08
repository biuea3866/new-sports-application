-- V40: B2B 파트너 연동 — partner_audit_log 테이블 생성
-- 근거: design-db.md "3. partner_audit_log — 파트너 활동 감사 (V40, append-only)"
-- 선행 조건: V38 (partner 테이블 존재, 논리 참조)
-- append-only immutable 테이블: created_at만 보유 (updated_*/deleted_* 없음)
-- mcp_audit_logs와 테이블 미공유 (도메인 격리) — 컬럼 구성은 mcp 선례 준용
-- 보관 정책: 90일 (hard-delete 스케줄러는 본 설계 범위 외 별도 티켓)
-- 신규 가산 테이블 — 기존 테이블 무영향, 온라인 안전 (CREATE TABLE)
-- 롤백: DROP TABLE partner_audit_log;

CREATE TABLE partner_audit_log (
    id                BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'PK',
    partner_id        BIGINT       NOT NULL                 COMMENT '요청 파트너 (partner.id 참조, 물리 FK 없음)',
    user_id           BIGINT       NOT NULL                 COMMENT '연동 User id (owner로 귀속된 계정)',
    http_method       VARCHAR(10)  NOT NULL                 COMMENT 'HTTP 메서드: GET/POST/PATCH/PUT/DELETE',
    request_path      VARCHAR(512) NOT NULL                 COMMENT '요청 경로 (쿼리스트링 제외)',
    target_resource   VARCHAR(255) NULL                     COMMENT '대상 리소스 식별자 (예: productId, 파싱 실패 시 NULL)',
    status_code       INT          NOT NULL                 COMMENT '응답 HTTP 상태 코드 (201/401/403/404 등)',
    latency_ms        INT          NOT NULL                 COMMENT '처리 소요 시간 (밀리초)',
    ip_addr           VARCHAR(45)  NULL                     COMMENT '클라이언트 IP 주소 (IPv4/IPv6, mcp 선례 길이)',
    client_user_agent VARCHAR(500) NULL                     COMMENT 'User-Agent 문자열 (mcp 선례 길이)',
    called_at         DATETIME(6)  NOT NULL                 COMMENT '요청 시각 (UTC, 조회·정렬 기준)',
    created_at        DATETIME(6)  NOT NULL                 COMMENT '레코드 적재 시각 (UTC, append-only)',
    PRIMARY KEY (id),
    -- A1: ESR 순서 — Equality(partner_id) → Range/Sort(called_at). 운영자 감사 조회
    INDEX idx_partner_audit_log_partner_id_called_at (partner_id, called_at) COMMENT '파트너별 감사 로그 기간 범위 조회 + 최신순 정렬',
    -- A2: 전역 called_at 범위 스캔 (A1 복합은 선두가 partner_id라 부적합) — 90일 보관 정책 만료 삭제
    INDEX idx_partner_audit_log_called_at (called_at) COMMENT '90일 보관 정책: 만료 row 범위 스캔·삭제'
) COMMENT='B2B 파트너 활동 감사 로그 (append-only, 90일 보관)';

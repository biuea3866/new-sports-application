-- V38: B2B 파트너 연동 — partner 테이블 생성
-- 근거: design-db.md "1. partner — 협력사 신원 (V38)"
-- 신규 가산 테이블 — 기존 테이블 무영향, 온라인 안전 (CREATE TABLE)
-- 롤백: DROP TABLE partner;
-- (V39 partner_api_key, V40 partner_audit_log가 partner_id로 이 테이블을 논리 참조하므로
--  전체 롤백 시 생성 역순으로 V40 → V39 → V38 순서로 DROP)

CREATE TABLE partner (
    id              BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'PK',
    name            VARCHAR(255) NOT NULL                 COMMENT '협력사 표시명 (운영자 입력)',
    status          VARCHAR(32)  NOT NULL                 COMMENT '상태: ACTIVE | SUSPENDED (ENUM 금지 → VARCHAR)',
    linked_user_id  BIGINT       NOT NULL                 COMMENT '연동 전용 User id (users.id 참조, 물리 FK 없음)',
    version         BIGINT       NOT NULL DEFAULT 0       COMMENT '낙관락(@Version) — 동시 상태 전이 lost-update 방지',
    created_at      DATETIME(6)  NOT NULL                 COMMENT '생성 시각 (UTC)',
    created_by      BIGINT       NULL                     COMMENT '생성자 user_id (ADMIN)',
    updated_at      DATETIME(6)  NOT NULL                 COMMENT '마지막 수정 시각 (UTC)',
    updated_by      BIGINT       NULL                     COMMENT '마지막 수정자 user_id',
    PRIMARY KEY (id),
    -- 파트너 1개 = 연동 User 1개(1:1). UNIQUE가 무결성(중복 연동 방지)과 역조회(P2)를 동시 충족
    UNIQUE KEY uq_partner_linked_user_id (linked_user_id) COMMENT '연동 User당 파트너 1개 보장 + 운영자 역조회'
) COMMENT='B2B 파트너 협력사 신원';

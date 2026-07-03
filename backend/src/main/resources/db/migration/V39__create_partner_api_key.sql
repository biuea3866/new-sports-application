-- V39: B2B 파트너 연동 — partner_api_key 테이블 생성
-- 근거: design-db.md "2. partner_api_key — 인증 키 (V39)"
-- 선행 조건: V38 (partner 테이블 존재, 논리 참조)
-- 신규 가산 테이블 — 기존 테이블 무영향, 온라인 안전 (CREATE TABLE)
-- 롤백: DROP TABLE partner_api_key;

CREATE TABLE partner_api_key (
    id             BIGINT       NOT NULL AUTO_INCREMENT  COMMENT 'PK. partner_<id>_<random> 키 문자열의 <id>가 이 값 (인증 필터가 parseKeyId로 추출)',
    partner_id     BIGINT       NOT NULL                 COMMENT '소유 파트너 (partner.id 참조, 물리 FK 없음)',
    key_hash       VARCHAR(255) NOT NULL                 COMMENT 'BCrypt 해시 (평문은 발급 시 1회만 노출)',
    status         VARCHAR(32)  NOT NULL                 COMMENT '상태: ACTIVE | REVOKED (ENUM 금지 → VARCHAR)',
    revoked_at     DATETIME(6)  NULL                     COMMENT '폐기·재발급 시각 (NULL=활성)',
    last_used_at   DATETIME(6)  NULL                     COMMENT '마지막 인증 성공 시각 (인증 필터가 갱신)',
    created_at     DATETIME(6)  NOT NULL                 COMMENT '발급 시각 (UTC)',
    created_by     BIGINT       NULL                     COMMENT '발급자 user_id (ADMIN)',
    PRIMARY KEY (id),
    -- K2: partner_id(고카디널리티, 등가) 선두 → status(저카디널리티) 후행 — 재발급 시 구 ACTIVE 키 조회
    INDEX idx_partner_api_key_partner_id_status (partner_id, status) COMMENT '파트너별 활성 키 조회 (재발급 시 구 ACTIVE 키 확인)',
    -- K3: 해시 재사용·충돌 방지 무결성 제약 (조회용 아님 — 인증은 PK 경유)
    UNIQUE KEY uq_partner_api_key_key_hash (key_hash) COMMENT '키 해시 전역 유일성 보장'
) COMMENT='B2B 파트너 API 인증 키';

-- V41: 피처 플래그 — feature_flags · feature_flag_audit_logs 테이블 생성
-- 근거: design-db.md "테이블 정의" (../design-db.md), 티켓 DB-01-create-feature-flags.md
-- 2 테이블 = 1 마이그레이션 파일 (feature_flags SSOT + feature_flag_audit_logs append-only)
-- feature_flags: 플래그 상태·평가 전략 SSOT. version BIGINT 낙관락 (@Version Long 정합)
-- feature_flag_audit_logs: append-only immutable 테이블 — created_*/updated_*/deleted_* 없음, occurred_at만 보유
-- 인덱스는 정확히 2개만 (design-db 확정) — status 인덱스는 저카디널리티·저볼륨 근거로 미채택
-- 신규 가산 테이블 2개 — 기존 테이블 무영향, 온라인 안전 (CREATE TABLE)
-- 배포 순서: 스키마 먼저 → 코드 → 데이터 (expand-contract, design-db "Release Scenario")
-- 롤백: DROP TABLE feature_flag_audit_logs; DROP TABLE feature_flags;

CREATE TABLE feature_flags (
    id              BIGINT       NOT NULL AUTO_INCREMENT      COMMENT 'PK',
    flag_key        VARCHAR(100) NOT NULL                     COMMENT '평가 키 (예: demo.feature.hello). UNIQUE — 평가 조회(findByKey)·중복 검증(existsByKey) 대상',
    flag_type       VARCHAR(32)  NOT NULL                     COMMENT '종류: RELEASE|OPERATIONAL|EXPERIMENT|ENTITLEMENT (ENUM 금지 → VARCHAR, 값 검증은 애플리케이션 FeatureFlagType)',
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'    COMMENT '상태: ACTIVE|ARCHIVED (ENUM 금지 → VARCHAR). 생성 시 ACTIVE',
    description     VARCHAR(500) NULL                         COMMENT '플래그 설명 (운영자 입력, 선택)',
    strategy_config TEXT         NOT NULL                     COMMENT '평가 전략 JSON 문자열 (sealed EvaluationStrategy ↔ AttributeConverter). JSON 컬럼 금지 → TEXT',
    version         BIGINT       NOT NULL DEFAULT 0           COMMENT '낙관락(@Version) — 관리 API 동시 수정 lost-update 방지. BIGINT(Long) 확정, BE-01 @Version Long 정합',
    created_at      DATETIME(6)  NOT NULL                     COMMENT '생성 시각 (UTC)',
    created_by      BIGINT       NULL                         COMMENT '생성자 user_id (ADMIN). 물리 FK 없음',
    updated_at      DATETIME(6)  NOT NULL                     COMMENT '마지막 수정 시각 (UTC)',
    updated_by      BIGINT       NULL                         COMMENT '마지막 수정자 user_id. 물리 FK 없음',
    PRIMARY KEY (id),
    UNIQUE KEY uq_feature_flags_flag_key (flag_key) COMMENT '평가·중복검증 조회(findByKey/existsByKey) + 중복 방지'
) COMMENT='피처 플래그 상태·평가 전략 SSOT';

CREATE TABLE feature_flag_audit_logs (
    id               BIGINT       NOT NULL AUTO_INCREMENT     COMMENT 'PK',
    flag_key         VARCHAR(100) NOT NULL                    COMMENT '대상 플래그 키 (feature_flags.flag_key 논리 참조, 물리 FK 없음). 플래그 archive 후에도 이력 독립 보존',
    change_type      VARCHAR(20)  NOT NULL                    COMMENT '변경 종류: CREATED|UPDATED|ARCHIVED|ACTIVATED (ENUM 금지 → VARCHAR)',
    actor_user_id    BIGINT       NOT NULL                    COMMENT '변경자 user_id (SecurityContext principal.id). 물리 FK 없음',
    before_snapshot  TEXT         NULL                        COMMENT '변경 전 값 JSON 문자열 (FeatureFlagSnapshot ↔ Converter). CREATED는 before 없음 → NULL. JSON 컬럼 금지 → TEXT',
    after_snapshot   TEXT         NOT NULL                    COMMENT '변경 후 값 JSON 문자열 (FeatureFlagSnapshot). 모든 변경에 존재. JSON 컬럼 금지 → TEXT',
    occurred_at      DATETIME(6)  NOT NULL                    COMMENT '변경 발생 시각 (UTC). 조회·정렬 기준, 적재 시각과 동일 (append-only, 별도 created_at 미도입)',
    PRIMARY KEY (id),
    INDEX idx_feature_flag_audit_logs_flag_key_occurred_at (flag_key, occurred_at) COMMENT 'ESR: flag_key(Equality 선두)+occurred_at(Sort) — 플래그별 이력 최신순 페이징(findByFlagKey)'
) COMMENT='피처 플래그 변경 감사 이력 (append-only)';

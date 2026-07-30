-- V42: alerting 도메인 — alerts table (지능형 장애 알림 이력, FR-9)
-- 설계 근거: /Users/biuea/Desktop/dpdpdndn/프로젝트/스포츠앱/지능형 장애 알림/TDD.md §ERD·§Detail Design
--            /Users/biuea/Desktop/dpdpdndn/프로젝트/스포츠앱/지능형 장애 알림/Tickets/DB-01-create-alerts-table.md
-- 번호 재조정 방침: origin/dev 최신이 V41(create_limited_drops) → 본 티켓은 V42 (TDD.md:371 머지 순서에 따라 재배정 가능)
-- 롤백: DROP TABLE alerts; (참조 코드 미배포 상태(alerting.enabled=false) — 안전, expand-contract 1단계 신규 가산 테이블)
-- 통합 블로커 수정: Alert 엔티티가 JpaAuditingBase(domain.common)를 상속하며 created_by/updated_by/deleted_at/deleted_by
-- 4개 audit 컬럼을 추가로 매핑한다. ddl-auto=validate 환경에서 누락 시 Hibernate 스키마 검증 실패로 전체
-- @SpringBootTest 컨텍스트 기동이 막힌다 — V41(limited_drops)·V7(notifications)의 audit 컬럼 블록과 동일 패턴으로 보강.
-- 롤백(본 수정분만): ALTER TABLE alerts DROP COLUMN created_by, DROP COLUMN updated_by, DROP COLUMN deleted_at, DROP COLUMN deleted_by;

CREATE TABLE alerts
(
    id                 BIGINT      NOT NULL AUTO_INCREMENT COMMENT 'PK',
    signal_key         VARCHAR(255) NOT NULL COMMENT '쿨다운/dedup 단위 신호 키 = endpoint+source+severity 조합 (AlertSignal.cooldownKey 상응, Redis 키와 별개로 이력 조회용)',
    endpoint           VARCHAR(255) NOT NULL COMMENT '알림 대상 API 엔드포인트 경로',
    source             VARCHAR(20) NOT NULL COMMENT '알림 발생 축 — latency|oversell|deployment|self_check (ENUM 금지 → VARCHAR)',
    severity           VARCHAR(20) NOT NULL COMMENT '심각도 — info|warn|critical (ENUM 금지 → VARCHAR)',
    env                VARCHAR(20) NOT NULL COMMENT '발생 환경 — local|dev|prod (ENUM 금지 → VARCHAR)',
    status             VARCHAR(20) NOT NULL COMMENT '상태 — RAISED|ANALYZED|FALLBACK|DELIVERED|DELIVERY_FAILED (ENUM 금지 → VARCHAR)',
    analysis           TEXT        NULL COMMENT 'LLM 원인분석 결과(IncidentAnalysis JSON, JsonStringType) — RAISED 생성 시점엔 미존재, process() 완료 후 attachAnalysis로 채워짐 (JSON 컬럼 금지 → TEXT)',
    analysis_included  TINYINT(1)  NULL COMMENT '실제 LLM 분석 포함 여부(폴백이면 0) — analysis와 함께 process() 완료 후 채워짐, RAISED 시점엔 NULL (BOOLEAN 금지 → TINYINT(1))',
    raised_at          DATETIME(6) NOT NULL COMMENT '알림 최초 발생(RAISED 전이) 시각',
    delivered_at       DATETIME(6) NULL COMMENT '발송 완료(DELIVERED) 시각 — 발송 전/실패 시 NULL',
    version            BIGINT      NOT NULL DEFAULT 0 COMMENT '낙관락(@Version) — 상태 전이 동시성 제어',
    created_at         DATETIME(6) NOT NULL COMMENT '행 생성 시각 (감사)',
    created_by         BIGINT      NULL COMMENT '생성자 user_id (JpaAuditingBase.createdBy, 시스템 자동 생성은 NULL)',
    updated_at         DATETIME(6) NOT NULL COMMENT '마지막 수정 시각 (감사)',
    updated_by         BIGINT      NULL COMMENT '마지막 수정자 user_id (JpaAuditingBase.updatedBy, 시스템 자동 수정은 NULL)',
    deleted_at         DATETIME(6) NULL COMMENT '소프트 삭제 시각 (JpaAuditingBase 공통 소프트 삭제 컨벤션, 미삭제 시 NULL)',
    deleted_by         BIGINT      NULL COMMENT '삭제자 user_id (JpaAuditingBase.deletedBy, 시스템 자동 삭제는 NULL)',
    PRIMARY KEY (id),
    -- 인덱스 근거: TDD.md §Observability/사후 분석 조회 패턴 "env+source+raised_at 범위" 단일 쿼리만 확인됨.
    -- 컬럼 순서: env(등치, distinct 3: local/dev/prod)·source(등치, distinct 4)를 선행 배치해 범위 스캔(raised_at) 전 행 수를 좁히고,
    -- 두 등치 컬럼 중 env를 prod/dev 운영 분리 필터로 항상 우선 사용한다는 조회 관례(TDD.md "env 태그" 전제)에 따라 env를 source보다 앞에 둔다.
    -- range 컬럼(raised_at)은 인덱스 마지막에 위치해야 등치 컬럼 이후 정렬된 범위 스캔이 가능하다.
    INDEX idx_alerts_env_source_raised_at (env, source, raised_at),
    -- soft-delete 컨벤션(JpaAuditingBase 조회는 WHERE deleted_at IS NULL) — V41·V7 선례와 동일하게 조회 필터 인덱스 추가.
    INDEX idx_alerts_deleted_at (deleted_at)
) COMMENT '지능형 장애 알림 이력 (신호·심각도·소스·LLM 원인분석·발송 상태, FR-9)';

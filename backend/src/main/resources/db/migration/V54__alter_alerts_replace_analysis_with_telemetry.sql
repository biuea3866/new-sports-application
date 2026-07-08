-- V54: alerts — LLM 원인분석(analysis/analysis_included) 제거, 원지표 스냅샷(telemetry)으로 대체
-- 배경: 지능형 장애 알림에서 LLM 원인분석을 완전히 제거하고, 알림 본문의 "원인"을
--       Prometheus/Loki/Tempo 조회 결과(TelemetrySnapshot)로 채운다 (feat/alerting-telemetry-raw-no-llm).
-- 배포 순서: 스키마 먼저(본 마이그레이션) → 코드(Alert/AlertDomainService) → 무중단.
--   alerting.enabled 기본값이 false이고 개인 로컬 환경이라 컬럼 DROP으로 인한 데이터 손실 위험이 낮다.
--   (expand-contract 원칙상 이상적으로는 컬럼 보존 후 별도 제거 단계를 두지만, 운영 트래픽이 없는
--   개인 프로젝트 특성상 단일 단계로 처리한다.)
-- 번호 재검사: 현재 origin/dev 최신은 V53. 동시 dev 머지 시 번호 충돌 가능하므로,
--   머지 직전 `ls migration | grep -oE '^V[0-9]+' | sort -n | uniq -d`로 중복이 없는지 재확인한다.
-- 롤백(역방향 DDL): 아래를 그대로 실행한다.
--   ALTER TABLE alerts
--       DROP COLUMN telemetry,
--       ALGORITHM = INPLACE, LOCK = NONE;
--   ALTER TABLE alerts
--       ADD COLUMN analysis TEXT NULL COMMENT 'LLM 원인분석 결과(IncidentAnalysis JSON, JsonStringType)',
--       ADD COLUMN analysis_included TINYINT(1) NULL COMMENT '실제 LLM 분석 포함 여부(폴백이면 0)',
--       ALGORITHM = INPLACE, LOCK = NONE;
--   ALTER TABLE alerts
--       MODIFY COLUMN status VARCHAR(20) NOT NULL COMMENT '상태 — RAISED|ANALYZED|FALLBACK|DELIVERED|DELIVERY_FAILED (ENUM 금지 → VARCHAR)';

ALTER TABLE alerts
    DROP COLUMN analysis,
    DROP COLUMN analysis_included,
    ALGORITHM = INPLACE,
    LOCK = NONE;

ALTER TABLE alerts
    ADD COLUMN telemetry TEXT NULL COMMENT '원지표 스냅샷(TelemetrySnapshot JSON, JsonStringType) — Prometheus/Loki/Tempo 조회 결과. RAISED 생성 시점엔 미존재, process() 완료 후 attachTelemetry로 채워짐 (JSON 컬럼 금지 → TEXT)',
    ALGORITHM = INPLACE,
    LOCK = NONE;

-- status 값 집합이 ANALYZED|FALLBACK → ENRICHED로 단순화됐다(컬럼 타입은 VARCHAR(20)로 불변, 값 의미만 변경).
-- COMMENT-only 변경이라 MySQL 8.0에서 metadata-only(INSTANT)이나, 형제 ALTER와의 일관성을 위해 온라인 절 명시.
ALTER TABLE alerts
    MODIFY COLUMN status VARCHAR(20) NOT NULL COMMENT '상태 — RAISED|ENRICHED|DELIVERED|DELIVERY_FAILED (ENUM 금지 → VARCHAR, LLM 분석 제거로 ANALYZED/FALLBACK 이원화를 ENRICHED 단일 상태로 단순화)',
    ALGORITHM = INPLACE,
    LOCK = NONE;

-- 테이블 레벨 COMMENT 동기화 (V42의 'LLM 원인분석' 문구를 원지표 첨부로 갱신).
ALTER TABLE alerts
    COMMENT = '지능형 장애 알림 이력 (신호·심각도·소스·텔레메트리 원지표 스냅샷·발송 상태, FR-9)';

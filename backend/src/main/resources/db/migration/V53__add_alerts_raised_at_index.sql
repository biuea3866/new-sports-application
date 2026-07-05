-- V53: alerts 이력 보존 정책(90일, mcp_audit_logs 선례) 지원 인덱스 추가
-- 번호 재배정 완료: 원래 V52로 작성되었으나 dev 최신에 V52__create_feature_flags.sql(#227)이
--   이미 점유하고 있어 rebase 시 V53으로 재배정했다(통합 담당 처리 완료).
-- 근거: private-db-schema-convention "인덱스는 대상 쿼리가 근거" — 신규 정리 배치가
--       raised_at < cutoff 조건만으로 전체 행을 스캔한다(env·source 등치 조건 없음).
--       기존 idx_alerts_env_source_raised_at(env, source, raised_at)는 env·source 등치가
--       선행해야 range 스캔에 쓰이므로 이 배치 쿼리에는 사용되지 못한다 — 별도 단일 컬럼
--       인덱스가 필요하다(mcp_audit_logs의 idx_mcp_audit_logs_called_at 선례와 동일 패턴).
-- 배포 순서: 스키마 먼저 → 코드(PurgeExpiredAlertsUseCase/AlertRetentionScheduler) → 무중단.
--   기존 테이블에 인덱스만 추가하는 온라인 안전 변경(ALGORITHM=INPLACE, LOCK=NONE).
-- 롤백: ALTER TABLE alerts DROP INDEX idx_alerts_raised_at;

ALTER TABLE alerts
    ADD INDEX idx_alerts_raised_at (raised_at) COMMENT '보존 정책(기본 90일) 정리 배치의 raised_at < cutoff 범위 스캔',
    ALGORITHM = INPLACE,
    LOCK = NONE;

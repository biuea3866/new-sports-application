# DEF-003 부하 시드 SQL 3종 미존재 — LOAD-01·02·03 모두 사전 데이터 없이 실행됨

## 메타
- layer: INFRA
- severity: Major
- auto-fix-eligible: false
- source-scenario: LOAD-01, LOAD-02, LOAD-03
- detected-at: 2026-05-23T18:11:16+09:00
- environment: docker-compose.qa.yml + k6
- related-pr: none
- related-ticket: none

## 분류 근거
- `load-results/facility-search/threshold.txt:23` — "시드 데이터 없음(qa/load/seeds/facility-search.sql 파일 미존재)으로 DB 조회 자체는 빈 결과를 반환해야 하나, 인증 레이어에서 차단됨"
- `load-results/ticket-seat-select-spike/threshold.txt:20` — "시드 데이터(qa/load/seeds/ticket-seat-select.sql) 없음"
- `load-results/booking-create-throughput/threshold.txt:24` — "시드 데이터(qa/load/seeds/booking-create.sql) 없음"
- `ls qa/load/seeds/` → 디렉토리 자체 존재하지 않음 (exit code 1)
- `qa/load/README.md` 및 [qa-load-guide.md](../../../.claude/rules/qa-load-guide.md) — `qa/load/seeds/{flow-slug}.sql` 가 시드 영구 보존 경로로 명시
- 시나리오 md(`qa/load/scenarios/*.md`) 는 시드 SQL 을 사전 조건으로 기술하나, 파일 자체가 존재하지 않음 → **layer: INFRA (테스트 인프라 설정 누락)**.
- severity: 부하 시나리오 3개 모두 실제 latency/throughput 측정 불가 상태 → Major. Critical 은 아님(운영 영향 없음, QA 환경 회귀만 영향).
- auto-fix-eligible: INFRA 는 무조건 false ([defect-ticket-guide.md] 룰 표).

## 재현 단계
1. `ls -la qa/load/seeds/` 실행 → 디렉토리 존재하지 않음
2. `pnpm --filter qa-load run loadtest:facility-search` (또는 k6 직접 실행) → 401·404 즉시 응답 다수 발생
3. `load-results/{slug}/threshold.txt` 의 "원인 분석" 섹션에서 시드 미존재 명시

## 기대 동작
- `qa/load/seeds/facility-search.sql` 가 존재하며 facilities 데이터 N건 삽입
- `qa/load/seeds/ticket-seat-select.sql` 가 존재하며 event_id=1 이벤트 + 좌석 데이터 삽입
- `qa/load/seeds/booking-create.sql` 가 존재하며 시설·슬롯·사용자 데이터 삽입
- 부하 실행 전 단계에서 시드 SQL 자동 적용
- 부하 결과로 실제 latency/throughput 측정 가능

## 실제 동작
- 3개 시드 SQL 모두 미존재
- LOAD-01: 1,459 요청 전부 401 (단, 본 결함은 시드 부재만 다루며 401 자체는 DEF-001 의 영향)
- LOAD-02: 705,720 요청 4xx (event_id=1 데이터 없음 + headerAuth)
- LOAD-03: 2,561,175 요청 4xx (시설/슬롯/사용자 데이터 없음)
- 측정 latency(p95 8~20ms, RPS 24~21,000) 는 "즉시 4xx 응답" 기준이므로 실제 성능 아님

## 영향 범위
- 영향 사용자: QA 회귀 부하 측정만 영향 (운영 사용자 영향 없음)
- 영향 화면/엔드포인트: LOAD-01 `GET /facilities`, LOAD-02 `POST /events/{id}/seats/select`, LOAD-03 `GET /facilities/{id}/slots` + `POST /bookings`
- 데이터 영향: 없음 (시드 미적용으로 DB 빈 상태 유지)
- 부하 결과 신뢰성: 0 — 모든 측정값이 4xx 즉시 응답 기준이라 절대치도 회귀 추세도 사용 불가

## 아티팩트
- [LOAD-01 threshold.txt](../load-results/facility-search/threshold.txt)
- [LOAD-02 threshold.txt](../load-results/ticket-seat-select-spike/threshold.txt)
- [LOAD-03 threshold.txt](../load-results/booking-create-throughput/threshold.txt)
- [LOAD-01 raw.log](../load-results/facility-search/raw.log)
- [LOAD-02 raw.log](../load-results/ticket-seat-select-spike/raw.log)
- [LOAD-03 raw.log](../load-results/booking-create-throughput/raw.log)

## 의심 코드 경로
- `qa/load/seeds/` — 디렉토리 자체 신규 생성 필요. 3개 SQL 파일 작성 필요:
  - `qa/load/seeds/facility-search.sql` — `INSERT INTO facilities ...` (gu·type 다양화)
  - `qa/load/seeds/ticket-seat-select.sql` — event_id=1 이벤트 + 좌석 N개
  - `qa/load/seeds/booking-create.sql` — 시설·슬롯·테스트 사용자
- `qa/load/k6/{slug}.js` setup() 단계 또는 별도 pre-script 에서 시드 적용 흐름 확인 필요. 현재 setup()은 캐시 워밍업만 수행.
- `/qa` 파이프라인 — 시드 적용 단계가 실행되는지 점검 필요

## 사람 검토 권장 사항
- 시드 SQL 3종은 도메인 컨텍스트(워크스페이스 PK, slot 시간대 등) 가 필요하므로 사람이 작성. be-implementer 자동 호출 대상 아님.
- 시드 적용 자동화 결정 필요: docker-compose 의 MySQL init script vs k6 setup() 외부 호출 vs `/qa` 파이프라인 단계.
- 시드 적용 후 DEF-001(BE 401) 미해소 상태에서 재실행해도 LOAD-01 은 여전히 실패함 — DEF-001 선행 처리 필요.

# DEF-004 k6 헬퍼 임계 정책 불일치 — http_req_failed가 4xx 포함, 시나리오 md는 "5xx만"

## 메타
- layer: INFRA
- severity: Minor
- auto-fix-eligible: false
- source-scenario: LOAD-02, LOAD-03 (LOAD-01 도 동일 헬퍼 사용)
- detected-at: 2026-05-23T18:11:16+09:00
- environment: qa/load/k6/lib/metrics.js + 각 시나리오 md
- related-pr: none
- related-ticket: none

## 분류 근거
- `qa/load/k6/lib/metrics.js:38` — `thresholdsFor()` 가 `http_req_failed: [rate<${base.errorRate * 2}]` 를 자동 추가. k6 내장 `http_req_failed` 는 기본 동작이 **4xx + 5xx 모두를 실패로 집계**.
- `load-results/ticket-seat-select-spike/threshold.txt:6-9` — "http_req_failed 는 k6 내장 메트릭으로 4xx/5xx 모두 집계 / 시나리오 md 정책(5xx만 error로 집계)과 달리 4xx도 포함됨"
- `load-results/booking-create-throughput/threshold.txt:7-10` — 동일 진단
- 시나리오 md(`qa/load/scenarios/ticket-seat-select-spike.md`·`booking-create-throughput.md`) 는 커스텀 메트릭 `LOAD_XX_failures` 를 통해 "5xx 만" 정책을 표현
- 두 정책이 한 시나리오 안에서 공존 → 동일 RUN 결과에 대해 "PARTIAL"(http_req_failed FAIL) + "PASS"(LOAD_XX_failures PASS) 가 동시 보고됨
- 결함은 코드(테스트 헬퍼)에 있으며 도메인 BE/FE 가 아님 → **layer: INFRA (테스트 도구 설정)**.
- severity: 본 결함만으로는 실제 결함 탐지를 막지 않음(LOAD_XX_failures 는 정상 통과). 다만 "PARTIAL" 노이즈로 회귀 판독 가독성 저하 → Minor.
- auto-fix-eligible: INFRA + Minor 이므로 false.

## 재현 단계
1. 임의 k6 시나리오에서 `thresholdsFor("LOAD-XX", "simpleGet")` 호출
2. 부하 실행 중 BE 가 4xx 응답 다수 반환 (예: DEF-001·DEF-003 미해결 상태)
3. summary.json 의 두 임계 결과 확인:
   - `http_req_failed.thresholds["rate<0.01"]` → false (FAIL)
   - `LOAD_XX_failures.thresholds["rate<0.005"]` → true (PASS)
4. threshold.txt 의 "원인 분석" 에 정책 불일치 명시

## 기대 동작
- 시나리오 md 와 k6 스크립트의 error rate 정책이 일치
- "5xx 만 error" 정책이면 `http_req_failed` 임계를 두지 않거나, 또는 `http_req_failed` 의 setResponseCallback 으로 4xx 를 통과로 처리
- 또는 "4xx + 5xx" 정책으로 통일하고 시나리오 md 의 "5xx만" 문구 제거

## 실제 동작
- `lib/metrics.js#thresholdsFor` 는 자동으로 `http_req_failed: rate<${errorRate * 2}` 를 모든 시나리오에 부착
- 시나리오 md 는 "5xx 만 error" 라고 명시
- 결과: 정상 BE 동작에서도 4xx 가 정상 응답인 경우(예: 유효성 검증 실패) http_req_failed 임계 위반

## 영향 범위
- 영향 사용자: QA 부하 회귀 결과 판독자
- 영향 시나리오: 모든 k6 시나리오 (3개 + 향후 추가될 시나리오)
- 데이터 영향: 없음 (테스트 결과 메타에만 영향)
- 다만 본 결함이 LOAD-01·02·03 의 PARTIAL 상태의 직접적 결함은 아님 — DEF-001·DEF-003 가 본질적 원인. 본 결함은 "정책 불일치"로 결과 해석을 모호하게 함

## 아티팩트
- [LOAD-02 threshold.txt](../load-results/ticket-seat-select-spike/threshold.txt) — 정책 불일치 명시 라인
- [LOAD-03 threshold.txt](../load-results/booking-create-throughput/threshold.txt) — 동일 진단
- [LOAD-02 summary.json](../load-results/ticket-seat-select-spike/summary.json)
- [LOAD-03 summary.json](../load-results/booking-create-throughput/summary.json)

## 의심 코드 경로
- `qa/load/k6/lib/metrics.js:38` — `thresholdsFor()` 의 `http_req_failed` 임계 부착 로직
- `qa/load/scenarios/{slug}.md` 3개 — "측정 노이즈 경고" 또는 "검증" 섹션에서 error rate 정책 재정의
- [qa-load-guide.md](../../../.claude/rules/qa-load-guide.md) — 정책 통일 결정 필요

## 사람 검토 권장 사항
- 정책 결정 필요(둘 중 택일):
  - A안: "5xx 만 error" 통일 — `thresholdsFor()` 에서 `http_req_failed` 임계 제거, 또는 setResponseCallback 으로 4xx 정상화. LOAD_XX_failures 만 임계로 유지.
  - B안: "4xx + 5xx error" 통일 — 시나리오 md 의 "5xx 만" 문구 제거, LOAD_XX_failures 커스텀 메트릭 제거.
- 결정 후 qa-load-tester · qa-defect-router 가 사용하는 결과 판독 기준도 동일 정책으로 정렬 필요.
- 결정 전까지는 본 결함을 백로그로 유지(Minor).

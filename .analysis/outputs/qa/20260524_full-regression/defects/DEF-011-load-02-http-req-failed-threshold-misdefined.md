# DEF-011 (시나리오 md 결함) LOAD-02 ticket-seat-select-spike — http_req_failed threshold가 5xx 외 비즈니스 응답까지 집계

## 메타
- layer: AMBIGUOUS
- severity: Minor
- auto-fix-eligible: false
- source-scenario: LOAD-02
- detected-at: 2026-05-24T18:33:14+09:00
- environment: docker-compose.qa.yml (BE commit 74e82bf)
- related-pr: none
- related-ticket: none

## 분류 근거
- `load-report.md:34-41` — `http_req_failed rate<0.04` → 실측 98.19% 위반. 5xx 전용 `LOAD_02_failures`는 0.00%. checks "not 5xx" 100% 통과.
- 시나리오 md(`qa/load/scenarios/ticket-seat-select-spike.md:17`) 의도: "5xx만 error로 집계, 409는 정상". 실제 k6 옵션은 `qa/load/k6/lib/metrics.js`의 공통 헬퍼 `thresholdsFor`가 `http_req_failed: [rate<${errorRate * 2}]`를 자동 부여하여 2xx 외 전체를 집계 — **시나리오 md 의도와 k6 스크립트 공통 헬퍼 사이의 불일치**.
- BE 동작은 정상(5xx 0%). **layer: AMBIGUOUS** (코드 결함 아님 — 측정 정의 결함). severity: 부하 결함 분류 표상 BE 실제 위반 없음 → **Minor**.

## 재현 단계
1. `qa/load/k6/ticket-seat-select-spike.js` 실행 — 200 VU spike 시 409 응답 비율이 자연스럽게 높아짐
2. raw.log THRESHOLDS 섹션에서 `http_req_failed 'rate<0.04'` 위반 확인 (실측 98.19%)
3. `LOAD_02_failures`(5xx 전용 커스텀 메트릭)는 0% 확인 — BE는 정상 동작

## 기대 동작
시나리오 md `qa/load/scenarios/ticket-seat-select-spike.md`의 의도대로 5xx만 error rate로 집계되어 PASS 처리.

## 실제 동작
k6 공통 헬퍼가 `http_req_failed` 임계를 자동 부여 → 409(좌석 락 경쟁, 정상 비즈니스 응답)까지 실패로 집계 → 임계 위반. 결과적으로 PASS* 로 보고되지만 k6 exit code 0이라 자동 분류기에는 위반으로 보임.

## 영향 범위
- 영향 사용자: 없음 (측정 정의 결함)
- 영향 화면/엔드포인트: 없음 (LOAD-02 시나리오 정의)
- 데이터 영향: 없음

## 아티팩트
- [load raw.log](../load-results/ticket-seat-select-spike/raw.log)
- [load-report.md](../load-report.md) (LOAD-02 섹션)
- 시나리오 md: `qa/load/scenarios/ticket-seat-select-spike.md`
- 공통 헬퍼: `qa/load/k6/lib/metrics.js` `thresholdsFor()` 함수

## 의심 코드 경로
- `qa/load/k6/lib/metrics.js:30~40` — `thresholdsFor()`가 무조건 `http_req_failed`를 추가 → spike/conflict 시나리오에서는 5xx만 집계하도록 옵션화 필요
- `qa/load/k6/ticket-seat-select-spike.js` — 스크립트별 `http_req_failed` 제거 또는 `http_req_failed{status:5xx}` 필터로 교체

## 자동 수정 지시
해당 없음 (`auto-fix-eligible: false`). 사람이 다음 중 하나를 선택:
- `qa/load/k6/lib/metrics.js`의 `thresholdsFor()`를 호출자에서 `http_req_failed` 임계 비활성화 옵션을 받도록 변경
- LOAD-02 스크립트에서 `http_req_failed` 임계를 명시적으로 override (5xx 필터 또는 제거)
- 시나리오 md `qa/load/scenarios/ticket-seat-select-spike.md`에 측정 정의를 명시 (5xx 전용 메트릭만 임계로 사용)

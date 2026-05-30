# DEF-010 LOAD-03 POST /bookings 부하 시 전수 500 (error rate 100%)

## 메타
- layer: BE
- severity: Major
- auto-fix-eligible: true
- source-scenario: LOAD-03
- detected-at: 2026-05-24T18:33:14+09:00
- environment: docker-compose.qa.yml (BE commit 74e82bf)
- related-pr: none
- related-ticket: none

## 분류 근거
- `load-results/booking-create-throughput/threshold.txt:7-9`:
  ```
  LOAD_03_failures: rate<0.01 → 실측 100.00% (목표 대비 100배 초과)
  http_req_failed:  rate<0.02 → 실측 49.99%
  ```
- `load-results/booking-create-throughput/raw.log` THRESHOLDS 섹션 — `LOAD_03_failures 'rate<0.01' rate=100.00%`, `check "create not 5xx": 0/55681`
- `load-report.md:24-31` — 응답 body 인용:
  ```json
  {"type":"https://errors.sports-application/internal-error","title":"Internal Error",
   "status":500,"detail":"An unexpected error occurred","instance":"/bookings"}
  ```
- POST /bookings만 전수 500, GET /facilities/{id}/slots는 200 정상 → POST 경로에 한정된 BE 오류. **layer: BE** / severity: 부하 결함 분류표상 p95(63% 여유)는 정상이나 error rate가 목표(1%) 대비 100배 초과 → 분류표 "p95이 목표의 200% 초과" 케이스는 아니지만 error rate 100%로 핵심 비즈니스 플로우 전수 실패 → **Major** (시나리오 md 상속).

## 재현 단계
1. `qa/load/seeds/booking-create.sql` 시드 미적용 상태(또는 슬롯 id 1~10000 범위가 시드와 불일치) 확인
2. `qa/load/k6/booking-create-throughput.js` 실행 (또는 단일 `POST /bookings` body `{slotId: <임의>}` 호출)
3. 응답 500 확인

## 기대 동작
시나리오 md(`qa/load/scenarios/booking-create-throughput.md:18`) — 5xx error rate < 1%, 슬롯 충돌은 409로 분리 처리.

## 실제 동작
POST /bookings 전수 500 (55,681건 / 55,681건). latency 임계(p95 314.5ms < 500, p99 463.2ms < 1500)는 통과하나 error rate가 측정 의미를 잃음.

## 영향 범위
- 영향 사용자: 부하 환경 한정 — 실제 사용자 영향 평가는 단일 호출 재현 결과에 의존
- 영향 화면/엔드포인트: `POST /bookings`
- 데이터 영향: 부하 시뮬레이션 — 실 booking row 생성 없음(500 반환). 부분 실패로 인한 데이터 일관성 영향 검증 필요

## 아티팩트
- [load raw.log](../load-results/booking-create-throughput/raw.log)
- [threshold.txt](../load-results/booking-create-throughput/threshold.txt)
- [summary.json](../load-results/booking-create-throughput/summary.json)
- [load-report.md](../load-report.md) (LOAD-03 섹션)

## 의심 코드 경로
- `backend/src/main/kotlin/com/sportsapp/presentation/booking/BookingApiController.kt` — POST /bookings 진입점
- `backend/src/main/kotlin/com/sportsapp/application/booking/` 하위 `Create*BookingUseCase.kt` — 존재하지 않는 slotId 처리 시 도메인 예외 vs 500 미분기 가능성
- `qa/load/seeds/booking-create.sql` 시드 적용 여부 — 적용 안 됐다면 부하 결함이 아닌 시드 결함 (사람 사전 확인 필요)

## 자동 수정 지시
대상 에이전트: be-implementer
작업 범위:
- 결함 한정 — `POST /bookings`의 비정상 입력(존재하지 않는 slotId, 시드 누락 상태) 응답이 500이 되지 않도록 도메인 예외 매핑만 추가. 슬롯 락·예약 로직 자체는 손대지 않음.
- 사전 확인: `qa/load/seeds/booking-create.sql` 적용 상태를 먼저 점검. 시드 누락이 단독 원인이라면 BE 변경 없이 시드 적용으로 해결 — 그 경우 be-implementer는 검증 테스트만 추가하고 BE 수정 없이 GREEN 확인.
- TDD 사이클: 존재하지 않는 slotId로 POST /bookings 호출 시 4xx 도메인 예외 반환을 단언하는 시나리오 테스트 먼저 RED → fix → GREEN.
- 테스트 위치 제안: `backend/src/test/kotlin/com/sportsapp/.../BookingApiControllerTest.kt` 또는 BookingUseCase 시나리오 테스트.
- 예상 변경 파일 수: 1~3개 (Controller 또는 UseCase 예외 매핑 + 테스트). 시드 결함이라면 BE 변경 0개 + 시드 적용 정책 메모.

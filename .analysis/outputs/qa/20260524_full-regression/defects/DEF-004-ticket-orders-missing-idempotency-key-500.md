# DEF-004 POST /ticket-orders Idempotency-Key 헤더 누락 시 500 (400 기대)

## 메타
- layer: BE
- severity: Major
- auto-fix-eligible: true
- source-scenario: E2E-05-E02
- detected-at: 2026-05-24T18:33:14+09:00
- environment: docker-compose.qa.yml (BE commit 74e82bf)
- related-pr: none
- related-ticket: none

## 분류 근거
- `logs/e2e-regression.log:90` — `✘ 87 ... E2E-05-E02 Idempotency-Key 없이 POST /ticket-orders 시 400 (15ms)`
- `logs/e2e-regression.log:332-340`:
  ```
  Expected: 400
  Received: 500
  > 184 |       expect(res.status()).toBe(400);
  ```
- DEF-003(`POST /payments`)과 동일 패턴 — 헤더 부재 시 500. **layer: BE** (입력 검증 누락) / severity 상속(Major).

## 재현 단계
1. 로그인된 user 토큰 확보
2. `Idempotency-Key` 헤더를 **포함하지 않고** `POST /ticket-orders` 호출
3. 응답 상태 코드 확인

## 기대 동작
시나리오 md(`qa/e2e/scenarios/ticket-event-purchase.md:38`) — `MissingIdempotencyKeyException`에 매핑되는 HTTP 400 반환.

## 실제 동작
HTTP 500 Internal Error. DEF-003과 동일 패턴(헤더 부재 → 도메인 예외 미발생 → 500).

## 영향 범위
- 영향 사용자: 티켓 발권 클라이언트
- 영향 화면/엔드포인트: `POST /ticket-orders`
- 데이터 영향: 없음

## 아티팩트
- [trace](../artifacts/ticket-event-purchase-E2E--c03ca-없이-POST-ticket-orders-시-400-chromium/trace.zip)
- [regression log](../logs/e2e-regression.log) (line 90, 330~354)

## 의심 코드 경로
- `backend/src/main/kotlin/com/sportsapp/presentation/ticketing/TicketOrderApiController.kt` — `Idempotency-Key` 헤더 검증 누락
- DEF-003과 공통 패턴 — 공통 인터셉터/필터에서 한 번에 처리 권장

## 자동 수정 지시
대상 에이전트: be-implementer
작업 범위:
- 결함 한정 — `POST /ticket-orders`의 `Idempotency-Key` 헤더 부재 검증만 추가.
- DEF-003과 동일 패턴이므로 공통 처리 후보 — 결정은 be-implementer에 위임하되 한 PR/티켓 안에서만.
- TDD 사이클: 헤더 누락 시 400 매핑 응답을 단언하는 시나리오 테스트 먼저 RED.
- 테스트 위치 제안: `backend/src/test/kotlin/com/sportsapp/.../TicketOrderApiControllerTest.kt`.
- 예상 변경 파일 수: 1~3개.

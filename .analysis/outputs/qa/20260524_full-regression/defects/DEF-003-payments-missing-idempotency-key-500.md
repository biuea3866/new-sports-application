# DEF-003 POST /payments Idempotency-Key 헤더 누락 시 500 (400 기대)

## 메타
- layer: BE
- severity: Major
- auto-fix-eligible: true
- source-scenario: E2E-04-E01
- detected-at: 2026-05-24T18:33:14+09:00
- environment: docker-compose.qa.yml (BE commit 74e82bf)
- related-pr: none
- related-ticket: none

## 분류 근거
- `logs/e2e-regression.log:68` — `✘ 65 ... E2E-04-E01 Idempotency-Key 없이 POST /payments 호출 시 400 (17ms)`
- `logs/e2e-regression.log:306-314`:
  ```
  Expected: 400
  Received: 500
  > 171 |       expect(res.status()).toBe(400);
  ```
- spec은 `MissingIdempotencyKeyException → 400` 매핑을 단언. 실제 500 → 헤더 부재가 도메인 예외로 변환되지 않고 NPE 또는 변환 미처리. **layer: BE** (입력 검증 누락) / severity 상속(Major).

## 재현 단계
1. 로그인된 user 토큰 확보 (`Authorization: Bearer ...`)
2. `Idempotency-Key` 헤더를 **포함하지 않고** `POST /payments` 호출 (body는 정상)
3. 응답 상태 코드 확인

## 기대 동작
시나리오 md(`qa/e2e/scenarios/payment-create-list.md:35`) — `MissingIdempotencyKeyException`에 매핑되는 HTTP 400 반환.

## 실제 동작
HTTP 500 Internal Error. 헤더 부재 시 `Idempotency-Key` 변수가 null 상태로 다운스트림에 전달되어 NPE 또는 미정의 예외가 발생하는 것으로 추정.

## 영향 범위
- 영향 사용자: 결제 클라이언트 — 헤더 누락 시 적절한 에러 코드를 받지 못함
- 영향 화면/엔드포인트: `POST /payments`
- 데이터 영향: 없음 (입력 검증 단계에서 거부되어야 하는 케이스)

## 아티팩트
- [trace](../artifacts/payment-create-list-E2E-04-2a54f-y-없이-POST-payments-호출-시-400-chromium/trace.zip)
- [regression log](../logs/e2e-regression.log) (line 68, 304~328)

## 의심 코드 경로
- `backend/src/main/kotlin/com/sportsapp/presentation/payment/PaymentApiController.kt` — `@RequestHeader("Idempotency-Key")` 바인딩이 `required=true`로 선언되지 않았거나 명시적 검증 누락
- 공통 `Idempotency-Key` 인터셉터/필터가 존재한다면 그 경로 — DEF-004(`POST /ticket-orders`)와 동일 패턴이므로 공통 처리 후보

## 자동 수정 지시
대상 에이전트: be-implementer
작업 범위:
- 결함 한정 — `POST /payments`의 `Idempotency-Key` 헤더 부재 검증만 추가. 인접 결제 로직 리팩토링 금지.
- 가능하면 DEF-004와 동일 패턴(`POST /ticket-orders`)을 한 공통 처리 지점에 모아 수정 — 단 같은 PR/티켓 범위 내에서만.
- TDD 사이클: 헤더 누락 시 400 + `MissingIdempotencyKeyException` 매핑 응답을 단언하는 시나리오 테스트 먼저 RED.
- 테스트 위치 제안: `backend/src/test/kotlin/com/sportsapp/.../PaymentApiControllerTest.kt`.
- 예상 변경 파일 수: 1~3개 (Controller 또는 공통 Validator + 예외 매핑 + 테스트).

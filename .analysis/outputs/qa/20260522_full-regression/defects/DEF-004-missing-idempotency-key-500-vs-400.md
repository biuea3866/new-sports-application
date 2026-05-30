# DEF-004 Idempotency-Key 헤더 누락 시 500 — MissingRequestHeaderException 미처리

## 메타
- layer: BE
- severity: Critical
- auto-fix-eligible: true
- source-scenario: E2E-04-E01 (POST /payments), E2E-05-E02 (POST /ticket-orders)
- detected-at: 2026-05-22T02:20:00+09:00
- environment: docker-compose.qa.yml (commit `e810357`, branch `feat/qa-pipeline`)
- related-pr: none
- related-ticket: none

## 분류 근거
- E2E error-context:
  - E2E-04-E01: `Expected: 400` / `Received: 500`
  - E2E-05-E02: `Expected: 400` / `Received: 500`
- BE 측 5xx — `GlobalExceptionHandler.handleUnknownException`이 잡은 일반 500. layer: BE 확정
- `TicketOrderApiController.kt:24`:
  ```kotlin
  @RequestHeader("Idempotency-Key") idempotencyKey: String?,
  ```
  → `required` 기본값 `true`. 헤더 부재 시 Spring이 `MissingRequestHeaderException` throw. `GlobalExceptionHandler`에 핸들러 없음 → 500
- `PaymentApiController.kt:38`:
  ```kotlin
  @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
  ```
  → 명시적 `required = false` — 이쪽은 헤더 미존재 시 null. `if (idempotencyKey.isNullOrBlank()) throw MissingIdempotencyKeyException()` 가 BAD_REQUEST(400) 으로 매핑되어야 하나 실제는 500.
  - 가설: `@RequestBody` Jackson 역직렬화 또는 `@Valid` 검증이 헤더 체크 이전에 실패하고 그 예외가 미처리 → 500. 또는 `userId = 1L` 하드코딩한 다음 호출 경로에서 NPE 등. 정확한 원인은 BE 로그 또는 raw 응답 본문에서 확인 필요
- 결제·발권 모두 핵심 비즈니스 → Critical
- 같은 ExceptionHandler 결함 라인이지만 두 컨트롤러 시그니처가 달라 fix가 둘 다 필요 — 한 결함으로 묶고 두 컨트롤러 모두 명시

## 재현 단계
1. BE 기동
2. `POST /ticket-orders` 호출 — `X-User-Id: 1` + `Content-Type: application/json`, **`Idempotency-Key` 헤더 없이**, body `{ "lockId": "...", "method": "CARD", "currency": "KRW" }`
3. **500 응답** (400 기대)
4. `POST /payments` 호출 — `Content-Type: application/json`, **`Idempotency-Key` 헤더 없이**, body `{ "orderType": "BOOKING", "orderId": 1, "method": "CARD", "amount": 50000, "currency": "KRW" }`
5. **500 응답** (400 기대)

## 기대 동작
HTTP 400 + ProblemDetail body:
```json
{ "status": 400, "code": "MISSING_IDEMPOTENCY_KEY", "detail": "Idempotency-Key header is required" }
```

## 실제 동작
HTTP 500 + ProblemDetail body:
```json
{ "status": 500, "code": "INTERNAL_ERROR", "detail": "An unexpected error occurred" }
```

## 영향 범위
- 영향 사용자: 결제·발권 통합 클라이언트 — 헤더 누락 시 5xx로 잘못된 시그널을 받음 (장애 알림 트리거, 재시도 정책 오작동)
- 영향 화면/엔드포인트: `POST /payments`, `POST /ticket-orders` (그리고 `POST /goods-orders` 도 같은 패턴인지 확인 필요 — `GoodsOrderApiController.kt:30`의 `@RequestHeader("Idempotency-Key") idempotencyKey: String,`)
- 데이터 영향: 없음 (요청 실패)

## 아티팩트
- [E2E-04-E01 error-context](../artifacts/playwright-output/payment-create-list-E2E-04-2a54f-y-없이-POST-payments-호출-시-400-chromium/error-context.md)
- [E2E-05-E02 error-context](../artifacts/playwright-output/ticket-event-purchase-E2E--c03ca-없이-POST-ticket-orders-시-400-chromium/error-context.md)

## 의심 코드 경로

| 파일 | 라인 | 역할 |
|---|---|---|
| `backend/src/main/kotlin/com/sportsapp/presentation/ticketing/TicketOrderApiController.kt` | 24 | `@RequestHeader("Idempotency-Key")` 가 `required = false` 명시 없음 → `MissingRequestHeaderException` |
| `backend/src/main/kotlin/com/sportsapp/presentation/payment/PaymentApiController.kt` | 38 | `required = false`로 선언되어 있으나 응답이 500 — 다른 경로에서 예외 발생 추정 (BE raw log 확인 필요) |
| `backend/src/main/kotlin/com/sportsapp/presentation/goods/GoodsOrderApiController.kt` | 30 | `String` (non-null) — 같은 결함 패턴 확인 필요 |
| `backend/src/main/kotlin/com/sportsapp/presentation/exception/GlobalExceptionHandler.kt` | 41-74 | `MissingRequestHeaderException` 핸들러 미정의 → `handleUnknownException` (500) 으로 fallback |
| `backend/src/main/kotlin/com/sportsapp/domain/payment/MissingIdempotencyKeyException.kt` | 1-11 | 도메인 예외는 BAD_REQUEST(400) 매핑 — 정상. 컨트롤러에서 throw가 일관되게 도달하는지 확인 |

가설:
- A) TicketOrderApiController는 `required = false` 추가 + 기존 null check 유지 → 400 매핑. PaymentApiController는 500의 진짜 원인을 로그에서 확인 (Jackson 디시리얼라이즈 실패 가능)
- B) 또는 `GlobalExceptionHandler`에 `@ExceptionHandler(MissingRequestHeaderException::class)` 추가 — 400 반환. 두 컨트롤러 모두 해결
- C) `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException` 도 같이 400으로 핸들러 등록 (방어적 추가) — 단 CLAUDE.md §3에 따라 결함과 무관한 핸들러는 추가 금지. 필요한 것만.

## 자동 수정 지시
대상 에이전트: be-implementer

작업 범위:
- 결함 한정 — Idempotency-Key 누락 → 400 매핑만 해결. 다른 ExceptionHandler 보강·컨트롤러 리팩토링 금지 (CLAUDE.md §3 정밀한 수정)
- TDD 사이클:
  1. **RED**: `GlobalExceptionHandlerIntegrationTest.kt` 또는 각 컨트롤러의 presentation 통합 테스트에 "Idempotency-Key 없이 POST → 400" 케이스 추가 (두 컨트롤러 각각)
  2. **GREEN**: `GlobalExceptionHandler`에 `MissingRequestHeaderException` 핸들러 추가 → 400 매핑. 또는 컨트롤러에 `required = false` 일관 적용 + null 분기에서 도메인 예외 throw
  3. **GREEN 검증**: E2E-04-E01, E2E-05-E02 모두 400 반환
- 테스트 위치 제안: `backend/src/test/kotlin/com/sportsapp/presentation/exception/GlobalExceptionHandlerIntegrationTest.kt` (공통 핸들러 보강 시), 또는 각 컨트롤러의 `*ApiControllerTest.kt`
- 예상 변경 파일 수: 2~4개 (handler + 2 controller + test). 4개 초과 시 사람 검토 권장
- **반드시 점검**: PaymentApiController 500의 진짜 원인 — `MissingIdempotencyKeyException`이 정말 throw 되는지 BE 로그 또는 디버그로 확인. throw가 안 되고 있다면 컨트롤러 로직 보강 필요

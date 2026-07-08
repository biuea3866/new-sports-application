# E2E-04 결제 생성 · 멱등성 · 내 결제 내역 (회귀 보강 — status 필터 enum 매핑)

> 영구 시나리오 `qa/e2e/scenarios/payment-create-list.md`의 1회성 보강본입니다.
> 기존 E2E-04-01~05 / R01~R02 / E01~E04는 영구 시나리오 그대로 회귀 실행하고,
> 본 파일은 PR #182(payments status 무효 enum → 500 fix, 런타임 재검증 미수행) 재검증용 신규 케이스만 추가합니다.

## 메타
- severity: Critical
- layer-hint: BE
- related-files:
  - backend/src/main/kotlin/com/sportsapp/presentation/payment/PaymentApiController.kt
  - backend/src/main/kotlin/com/sportsapp/presentation/exception/GlobalExceptionHandler.kt
  - backend/src/main/kotlin/com/sportsapp/domain/payment/PaymentStatus.kt
- related-ticket: none (PR #182 fix/qa-20260531-payments-status-enum)
- estimated-duration: 45s

## 사전 조건
- DB 시드: 없음 (status 필터 동작은 결제 시드 없이도 status 코드/스키마로 검증 — 빈 결과여도 200/400 단언 가능)
- 인증 상태: user-A (`X-User-Id` 헤더). `GET /payments/me`는 본인 결제 목록 조회.
- 환경 변수: 없음
- 주의: `PaymentApiController`가 `@RequestParam status: PaymentStatus?`를 직접 바인딩. 유효 enum = `PENDING/READY/COMPLETED/CANCELLED/FAILED/REFUNDED`. 무효 문자열은 `MethodArgumentTypeMismatchException` → `GlobalExceptionHandler`가 400으로 매핑(PR #182).

## 시나리오 (Given/When/Then 한 줄)
| ID | Given | When | Then |
|---|---|---|---|
| E2E-04-06 | user-A + 유효 status `COMPLETED` | `GET /payments/me?status=COMPLETED`를 호출할 때 | 200이 반환되고 content의 각 항목 status가 모두 COMPLETED다 (빈 결과여도 200) |
| E2E-04-07 | user-A + 유효 status `READY` | `GET /payments/me?status=READY`를 호출할 때 | 200이 반환된다 (유효 enum은 정상 처리 — 회귀 깨짐 방지) |

## 회귀 케이스 (변경 후에도 깨지면 안 되는 기존 동작)
| ID | 케이스 |
|---|---|
| E2E-04-R03 | (PR #182 회귀) 유효한 모든 PaymentStatus 값(`PENDING/READY/COMPLETED/CANCELLED/FAILED/REFUNDED`)으로 `GET /payments/me?status=` 조회 시 전부 200이 반환된다 |
| E2E-04-R04 | status 파라미터 미지정(`GET /payments/me`)은 전체 결과를 200으로 반환한다 (필터 없는 기존 동작 유지) |

## 엣지 케이스
| ID | 케이스 |
|---|---|
| E2E-04-E05 | (PR #182 재현) 무효 status 값 `GET /payments/me?status=INVALID_ANYTHING` 호출 시 **400 Bad Request가 반환되고 500이 아니다** |
| E2E-04-E06 | 소문자/형변형 무효 값 `GET /payments/me?status=paid` (대소문자·존재하지 않는 enum) 호출 시 400이 반환된다 |
| E2E-04-E07 | 빈 status 값 `GET /payments/me?status=` 호출 시 500이 아닌 200(필터 미적용으로 처리) 또는 400으로 일관 응답한다 |

## 보강 근거 (변경 표면 추적)
- PR #182: payments status 필터에 무효 enum 문자열이 들어오면 Spring이 `MethodArgumentTypeMismatchException`을 던지고 기존엔 500으로 노출되던 결함을, `GlobalExceptionHandler.handleMethodArgumentTypeMismatchException`이 400으로 매핑하도록 수정.
- 직전 회귀(20260531)에서 fix는 통합테스트 7 pass로 코드레벨 검증됐으나 클린 통합 런타임 재검증은 미수행 → 본 회귀에서 **무효 status → 400(500 아님)**, **유효 enum 전부 → 200(회귀 깨짐 없음)**을 런타임으로 단언.
- 주의: 영구 시나리오 E2E-04-05는 `status=PAID`를 사용하나 실제 enum에 `PAID`가 없음(`COMPLETED`가 정답). 영구 시나리오 자체의 enum 값 오기재는 별도 정정 후보 — 본 보강본은 실제 enum 값을 사용한다.

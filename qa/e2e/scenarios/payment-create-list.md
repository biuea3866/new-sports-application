# E2E-04 결제 생성 · 멱등성 · 내 결제 내역

## 메타
- severity: Critical
- layer-hint: FULL-STACK
- related-files:
  - backend/src/main/kotlin/com/sportsapp/presentation/payment/PaymentApiController.kt
  - backend/src/main/kotlin/com/sportsapp/presentation/booking/BookingApiController.kt
- related-ticket: none
- estimated-duration: 1m

## 사전 조건
- DB 시드: `qa/e2e/fixtures/booking-pending.sql` (user-A의 PENDING booking 1건 + 결제 대상 금액 50000원)
- 인증 상태: user-A
- 환경 변수: 결제 게이트웨이 stub 모드

## 시나리오 (Given/When/Then 한 줄)
| ID | Given | When | Then |
|---|---|---|---|
| E2E-04-01 | user-A + 신규 `Idempotency-Key: idem-001` | `POST /payments`에 정상 페이로드를 보낼 때 | 201 Created와 payment id·status=PENDING/COMPLETED가 반환된다 |
| E2E-04-02 | E2E-04-01과 동일한 `Idempotency-Key: idem-001` | `POST /payments`를 같은 페이로드로 재호출할 때 | 새 결제가 생기지 않고 E2E-04-01과 같은 payment id가 반환된다 |
| E2E-04-03 | E2E-04-01의 payment id | `GET /payments/{id}`를 호출할 때 | 200과 해당 payment 상세가 반환된다 |
| E2E-04-04 | user-A의 payment 1건 존재 | `GET /payments/me`를 호출할 때 | totalElements=1, createdAt DESC 정렬로 반환된다 |
| E2E-04-05 | `status=COMPLETED` 필터 | `GET /payments/me?status=COMPLETED`를 호출할 때 | COMPLETED 상태만 필터링되어 반환된다 |

## 회귀 케이스 (변경 후에도 깨지면 안 되는 기존 동작)
| ID | 케이스 |
|---|---|
| E2E-04-R01 | 결제 생성 응답 시 createdAt이 ISO-8601 UTC 형식으로 직렬화된다 |
| E2E-04-R02 | `GET /payments/me`의 기본 정렬이 createdAt DESC를 유지한다 |

## 엣지 케이스
| ID | 케이스 |
|---|---|
| E2E-04-E01 | `Idempotency-Key` 헤더 없이 `POST /payments` 호출 시 `MissingIdempotencyKeyException`에 매핑되는 400이 반환된다 |
| E2E-04-E02 | 다른 사용자(user-B)의 payment id로 `GET /payments/{id}` 호출 시 403/404가 반환된다 |
| E2E-04-E03 | `paidAtFrom`이 `paidAtTo`보다 이후 시각인 잘못된 범위로 조회 시 400 또는 빈 결과가 반환된다 |
| E2E-04-E04 | 결제 게이트웨이 stub이 5xx를 반환할 때 payment 상태가 FAILED로 기록된다 |

# E2E-05 경기 티켓 좌석 선택 · 발권

## 메타
- severity: Critical
- layer-hint: FULL-STACK
- related-files:
  - backend/src/main/kotlin/com/sportsapp/presentation/ticketing/EventApiController.kt
  - backend/src/main/kotlin/com/sportsapp/presentation/ticketing/TicketOrderApiController.kt
  - web/app/portal/events/page.tsx
  - web/app/portal/events/[id]/page.tsx
- related-ticket: none
- estimated-duration: 2m

## 사전 조건
- DB 시드: `qa/e2e/fixtures/event-with-seats.sql` (event 1건 OPEN 상태 + 좌석 100석, 모두 AVAILABLE)
- 인증 상태: user-A, user-B
- 환경 변수: 좌석 락 TTL=10분

## 시나리오 (Given/When/Then 한 줄)
| ID | Given | When | Then |
|---|---|---|---|
| E2E-05-01 | 미인증 사용자 | `GET /events?status=OPEN`을 호출할 때 | 200과 OPEN 상태 event 목록이 startsAt 오름차순으로 반환된다 |
| E2E-05-02 | event id 1 | `GET /events/1`을 호출할 때 | 200과 좌석 구성·잔여 좌석 수가 포함된 상세가 반환된다 |
| E2E-05-03 | user-A + seatIds `[101,102]` | `POST /events/1/seats/select`를 호출할 때 | 200과 lockId가 반환되고 좌석 상태는 LOCKED로 전이된다 |
| E2E-05-04 | E2E-05-03의 lockId + `Idempotency-Key: tic-001` | `POST /ticket-orders`에 lockId·method·currency를 보낼 때 | 202 Accepted와 ticket order id가 반환된다 |
| E2E-05-05 | user-A가 좌석 락만 잡고 미발권 상태 | `POST /events/1/seats/release`를 같은 seatIds로 호출할 때 | 204 No Content가 반환되고 좌석은 AVAILABLE로 복귀한다 |

## 회귀 케이스 (변경 후에도 깨지면 안 되는 기존 동작)
| ID | 케이스 |
|---|---|
| E2E-05-R01 | `GET /events` 응답의 startsAt이 명시적 UTC(`Z` suffix)로 직렬화된다 |
| E2E-05-R02 | `Idempotency-Key`로 재호출 시 동일 ticket order id가 반환되고 좌석은 중복 차감되지 않는다 |

## 엣지 케이스
| ID | 케이스 |
|---|---|
| E2E-05-E01 | user-A가 LOCKED한 좌석을 user-B가 동시에 select 시도 시 한 쪽만 성공하고 다른 쪽은 409 또는 도메인 예외를 받는다 |
| E2E-05-E02 | `Idempotency-Key` 없이 `POST /ticket-orders` 호출 시 `MissingIdempotencyKeyException`에 매핑되는 400이 반환된다 |
| E2E-05-E03 | 존재하지 않는 event id로 `GET /events/{id}` 호출 시 404가 반환된다 |
| E2E-05-E04 | 좌석 락 TTL 경과 후 발권 시도 시 락 만료 도메인 예외가 반환된다 |
| E2E-05-E05 | 빈 seatIds `[]`로 select 호출 시 400 Bad Request가 반환된다 |

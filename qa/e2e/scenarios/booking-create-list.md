# E2E-03 시설 슬롯 예약 생성 · 조회

## 메타
- severity: Critical
- layer-hint: FULL-STACK
- related-files:
  - backend/src/main/kotlin/com/sportsapp/presentation/booking/BookingApiController.kt
  - backend/src/main/kotlin/com/sportsapp/presentation/booking/SlotApiController.kt
  - web/app/portal/bookings/page.tsx
  - web/app/portal/slots/page.tsx
- related-ticket: none
- estimated-duration: 1m 30s

## 사전 조건
- DB 시드: `qa/e2e/fixtures/facility-with-slots.sql` (시설 1건 + 비어있는 슬롯 3건, 전부 미래 시각)
- 인증 상태: user-A (일반 사용자), user-B (다른 일반 사용자)
- 환경 변수: 없음

## 시나리오 (Given/When/Then 한 줄)
| ID | Given | When | Then |
|---|---|---|---|
| E2E-03-01 | user-A로 인증 + 시드 슬롯 `slot-101` | `POST /bookings`에 `slotId=slot-101`을 보낼 때 | 202 Accepted와 booking id가 반환되고 상태는 PENDING이다 |
| E2E-03-02 | E2E-03-01에서 생성된 booking id | `GET /bookings/{id}`를 user-A 헤더로 호출할 때 | 200과 해당 booking 상세가 반환된다 |
| E2E-03-03 | user-A의 booking 1건이 존재 | `GET /bookings/me`를 호출할 때 | totalElements=1, 첫 항목이 E2E-03-01의 booking인 응답이 반환된다 |
| E2E-03-04 | `status=PENDING` 쿼리 파라미터 | `GET /bookings/me?status=PENDING`를 호출할 때 | PENDING 상태 booking만 필터링되어 반환된다 |

## 회귀 케이스 (변경 후에도 깨지면 안 되는 기존 동작)
| ID | 케이스 |
|---|---|
| E2E-03-R01 | 페이징 기본값(page=0, size=20)이 명시되지 않은 요청에서도 유지된다 |
| E2E-03-R02 | booking 생성 직후의 응답 status가 즉시 CONFIRMED가 아닌 PENDING이다 (비동기 결제 진행 전 상태) |

## 엣지 케이스
| ID | 케이스 |
|---|---|
| E2E-03-E01 | `X-User-Id` 헤더 없이 `POST /bookings` 호출 시 400/401이 반환된다 |
| E2E-03-E02 | 이미 booking된 슬롯에 user-B가 동시에 booking 요청 시 한 쪽만 성공하고 다른 쪽은 도메인 예외를 받는다 |
| E2E-03-E03 | user-A의 booking을 user-B가 `GET /bookings/{id}` 호출 시 403/404가 반환된다 |
| E2E-03-E04 | booking이 0건인 user-C가 `GET /bookings/me`를 호출하면 200과 빈 페이지가 반환된다 |

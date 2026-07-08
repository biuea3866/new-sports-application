# 회귀 시나리오 목록 — 20260524 full-regression

본 회귀에서 실행될 영구 회귀 스위트입니다. 변경 영향과 무관하게 전부 실행합니다.

## E2E 회귀 (8개)

| 시나리오 ID | 파일 | 도메인 | severity (대표) | 본 회귀 트리거 |
|---|---|---|---|---|
| E2E-01 | qa/e2e/scenarios/auth-register-login.md | Auth | Critical | full-regression |
| E2E-02 | qa/e2e/scenarios/booking-create-list.md | Booking | Critical | full-regression |
| E2E-03 | qa/e2e/scenarios/facility-search-list.md | Facility | Major | full-regression |
| E2E-04 | qa/e2e/scenarios/goods-cart-order.md | Goods | Critical | full-regression |
| E2E-05 | qa/e2e/scenarios/notification-message.md | Notification | Major | full-regression |
| E2E-06 | qa/e2e/scenarios/payment-create-list.md | Payment | Critical | full-regression |
| E2E-07 | qa/e2e/scenarios/portal-dashboard.md | Portal | Major | full-regression + Stock 리팩토링 영향 |
| E2E-08 | qa/e2e/scenarios/ticket-event-purchase.md | Ticketing | Critical | full-regression |

## LOAD 회귀 (3개)

| 시나리오 ID | 파일 | 대상 | objective | 본 회귀 트리거 |
|---|---|---|---|---|
| LOAD-01 | qa/load/scenarios/booking-create-throughput.md | POST /api/v1/bookings | throughput | full-regression |
| LOAD-02 | qa/load/scenarios/facility-search.md | GET /api/v1/facilities | latency | full-regression |
| LOAD-03 | qa/load/scenarios/ticket-seat-select-spike.md | POST /api/v1/ticket-events/.../seats | spike | full-regression |

## 변경 영향과의 매핑

| 변경 | 영향받는 회귀 시나리오 | 신규 1회성 시나리오 |
|---|---|---|
| BE: Stock repository 리팩토링 (`countOutOfStockByOwnerId` 위임 경로 분리) | E2E-07 portal-dashboard (특히 E2E-07-03: outOfStockProducts=2 카운트) | scenarios/e2e/portal-stock-count-on-dashboard.md |
| FE: zod 4.x 마이그레이션 (`invalid_type_error` → `message`, `errorMap` → `message`) | 영구 회귀 스위트에 portal 상품 폼 시나리오 없음 — 회귀 매핑 ∅ | scenarios/e2e/portal-product-form-validation.md |

## 부하 회귀 영향

본 회귀 변경은 부하 특성을 바꾸지 않습니다. 3개 LOAD 회귀를 그대로 실행하고 신규 부하 시나리오는 추가하지 않습니다. 사유는 `scenarios/load/_none.md` 참조.

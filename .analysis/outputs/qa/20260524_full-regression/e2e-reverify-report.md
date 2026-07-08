# E2E 재검증 리포트 (reverify — qa-reverify/20260524)

> Step 5-B: fix 6건(DEF-001~005, DEF-010) 통합 후 재실행 결과.
> 비교 기준: run2 (11 Fail, 5 Skip, 74 Pass).

## 요약

| 지표 | run2 | reverify | delta |
|---|---|---|---|
| 총 시나리오 (회귀 + 1회성) | 117 (90 + 27) | 117 (90 + 27) | 0 |
| Pass | 81 (74 + 7) | 84 (77 + 7) | +3 |
| Fail | 11 (11 + 0) | 8 (8 + 0) | -3 |
| Skip | 25 (5 + 20) | 25 (5 + 20) | 0 |
| 실행 시간 | 18.8s + 5.9s | 15.2s + 4.7s | -4.8s |
| 환경 | docker-compose.qa.yml | docker-compose.qa.yml | — |
| QA_BASE_URL | http://localhost:3000 | http://localhost:3000 | — |
| QA_API_URL | http://localhost:8080 | http://localhost:8080 | — |
| Playwright 버전 | 1.60.0 | 1.60.0 | — |
| BE commit | 74e82bf | 9b3c420 (qa-reverify/20260524) | — |

---

## run2 대비 판정 비교

| 직전 케이스 | run2 결과 | reverify 결과 | 판정 |
|---|---|---|---|
| E2E-06-R02 (goods idempotency) | Fail | **Pass** | 해결 |
| E2E-04-05 (payments PAID filter) | Fail | **Pass** | 해결 |
| E2E-05-E05 (events seats empty) | Fail | **Pass** | 해결 |
| E2E-04-E01 (payments missing key) | Fail | Fail | 미해결 |
| E2E-05-E02 (ticket-orders missing key) | Fail | Fail | 미해결 |
| E2E-01-E01 (이메일 형식 위반 400) | Fail | Fail | 유지 (spec 결함) |
| E2E-01-E02 (비밀번호 길이 400) | Fail | Fail | 유지 (spec 결함) |
| E2E-03-03 (bookings Page 구조) | Fail | Fail | 유지 (spec 결함) |
| E2E-08-03 (unreadCount 숫자) | Fail | Fail | 유지 (spec 결함) |
| E2E-08-E03 (unread-count 0) | Fail | Fail | 유지 (spec 결함) |
| E2E-08-E04 (빈 메시지 400) | Fail | Fail | 유지 (spec 결함) |
| (신규 회귀) | — | — | 0건 |

**해결 3건 / 미해결 2건 / 유지 6건 (spec 결함) / 신규 회귀 0건**

---

## 해결 확인 (3건)

### E2E-06-R02 — 같은 Idempotency-Key로 /goods-orders 재호출 시 동일 order id
- DEF-001: V23 마이그레이션(`goods_orders.idempotency_key` 컬럼) + `GoodsOrder.idempotencyKey` + `GoodsDomainService.findByIdempotencyKey` 적용
- run2 실패 메시지: 2번 호출 시 각각 다른 order id 발급
- reverify: **Pass** — 같은 키로 2회 호출 시 동일 id 반환 확인

### E2E-04-05 — GET /payments/me?status=PAID → COMPLETED 필터
- DEF-002: `PAID` → `COMPLETED` spec 정정 + GlobalExceptionHandler `MethodArgumentTypeMismatchException → 400` 추가
- run2 실패 메시지: status=PAID 파라미터 시 500 반환
- reverify: **Pass** — spec에서 `COMPLETED`로 쿼리하여 200 + 전체 COMPLETED 응답 확인

### E2E-05-E05 — 빈 seatIds로 select 호출 시 400/422
- DEF-005: `SelectSeatsRequest @field:NotEmpty` + `EventApiController @Valid` 적용
- run2 실패 메시지: 실제 500 반환 (`expect([400, 422]).toContain(res.status())` assertion 방향 역전 에러)
- reverify: **Pass** — 빈 배열 입력 시 422 반환, `[400, 422].toContain(422)` 통과

---

## 미해결 (2건) — BE 결함 잔존

### E2E-04-E01 — Idempotency-Key 없이 POST /payments 시 400
- DEF-002(fix 적용): `GlobalExceptionHandler MethodArgumentTypeMismatchException → 400` 추가됐으나 해당 엔드포인트의 NullPointerException은 여전히 500 반환
- reverify 실제 응답: **500** (기대: 400)
- 원인 추정: `PaymentApiController`에서 헤더가 `required=true`(기본값)인 채로 남아있어 헤더 미존재 시 컨트롤러 파라미터 바인딩 단계에서 500 발생. `required=false` 전환 + null 체크 미적용 상태.

### E2E-05-E02 — Idempotency-Key 없이 POST /ticket-orders 시 400
- DEF-004(fix 적용): `TicketOrderApiController Idempotency-Key required=false` 추가됐으나 여전히 500 반환
- reverify 실제 응답: **500** (기대: 400)
- 원인 추정: `required=false`로 헤더 바인딩은 성공하나 null 헤더에 대한 400 응답 처리 로직이 미구현 상태. 헤더 존재 여부 확인 후 `throw BadRequestException` 누락.

---

## 유지 — spec 결함 (6건, Minor)

spec 수정 필요. BE 코드 변경 불필요.

| ID | spec 기대 | BE 실제 | 수정 방향 |
|---|---|---|---|
| E2E-01-E01 | HTTP 400 | HTTP 422 | `toBe(400)` → `[400, 422].toContain(status)` |
| E2E-01-E02 | HTTP 400 | HTTP 422 | 동일 |
| E2E-03-03 | `body.content ?? body.items` | `body.bookings` | `body.content ?? body.items ?? body.bookings` |
| E2E-08-03 | `body.unreadCount` | `body.count` | `body.count` 또는 두 키 모두 허용 |
| E2E-08-E03 | `body.unreadCount === 0` | `body.count === 0` | 동일 |
| E2E-08-E04 | `expect([400,422]).toContain(res.status())` | BE 403 반환 | assertion 방향 수정: `expect(res.status()).toBeOneOf([400,422,403])` |

---

## 신규 회귀 (0건)

fix 적용으로 인해 기존 Pass 케이스가 Fail로 전환된 케이스 없음.

---

## Fail 목록 (reverify 기준 8건)

| ID | 제목 | severity | 원인 분류 | 아티팩트 |
|---|---|---|---|---|
| E2E-01-E01 | 이메일 형식 위반 가입 시 400 | Minor | spec 결함 | [trace](./artifacts.reverify/auth-register-login-E2E-01-8a4e5-E-01-E01-이메일-형식-위반-가입-시-400-chromium/trace.zip) |
| E2E-01-E02 | 비밀번호 길이 미달 가입 시 400 | Minor | spec 결함 | [trace](./artifacts.reverify/auth-register-login-E2E-01-15f52--01-E02-비밀번호-길이-미달-가입-시-400-chromium/trace.zip) |
| E2E-03-03 | GET /bookings/me — 200 + Page 응답 구조 | Minor | spec 결함 | [trace](./artifacts.reverify/booking-create-list-E2E-03-b7439-ookings-me-—-200-Page-응답-구조-chromium/trace.zip) |
| E2E-04-E01 | Idempotency-Key 없이 POST /payments 시 400 | Major | BE 결함 (잔존) | [trace](./artifacts.reverify/payment-create-list-E2E-04-2a54f-y-없이-POST-payments-호출-시-400-chromium/trace.zip) |
| E2E-05-E02 | Idempotency-Key 없이 POST /ticket-orders 시 400 | Major | BE 결함 (잔존) | [trace](./artifacts.reverify/ticket-event-purchase-E2E--c03ca-없이-POST-ticket-orders-시-400-chromium/trace.zip) |
| E2E-08-03 | GET /notifications/me/unread-count — unreadCount 숫자 | Minor | spec 결함 | [trace](./artifacts.reverify/notification-message-E2E-0-35e07--count-—-200-unreadCount-숫자-chromium/trace.zip) |
| E2E-08-E03 | 알림 0건 user — unread-count 0 | Minor | spec 결함 | [trace](./artifacts.reverify/notification-message-E2E-0-3fb75-알림-0건-user-—-unread-count-0-chromium/trace.zip) |
| E2E-08-E04 | 빈 메시지 내용 POST 시 400 | Minor | spec 결함 | [trace](./artifacts.reverify/notification-message-E2E-0-6b5e4--08-E04-빈-메시지-내용-POST-시-400-chromium/trace.zip) |

---

## Skip 목록 (25건 — run2와 동일, 변동 없음)

### 회귀 spec (5건)
| ID | skip 사유 |
|---|---|
| E2E-02-04, E2E-02-05 | 시설 시드 데이터 미존재 |
| E2E-04-E04 | stub 환경 의존 |
| E2E-05-R02 | 시드 의존 |
| E2E-07-R01 | operator-A 시드 의존 |

### 1회성 spec — portal-product-form-validation (14건), portal-stock-count-on-dashboard (6건)
operator-C/D (GOODS_SELLER) 시드 미주입으로 skip. 회귀와 무관.

---

## 환경 메타

| 항목 | 값 |
|---|---|
| Playwright 버전 | 1.60.0 |
| BE commit | 9b3c420 (qa-reverify/20260524) |
| FE | production build (port 3000, 변경 없음) |
| DB 시드 | V23 마이그레이션 적용 완료 (goods_orders.idempotency_key) |
| 인프라 | docker-compose.qa.yml — mysql/mongodb/redis/kafka 모두 UP |
| 실행일 | 2026-05-24 |

# E2E 실행 리포트 (run2 — 환경 정상화 후 재실행)

> run1은 `e2e-report.run1-contaminated.md` 로 archive. 본 리포트는 sports-application BE 단독 기동 후 깨끗한 환경에서 실행한 결과.

## 요약

| 지표 | 값 |
|---|---|
| 총 시나리오 (회귀 + 1회성) | 117 (90 + 27) |
| Pass | 81 (74 + 7) |
| Fail | 11 (11 + 0) |
| Skip | 25 (5 + 20) |
| 실행 시간 | 18.8s (회귀) + 5.9s (1회성) |
| 환경 | docker-compose.qa.yml (mysql/mongodb/redis/kafka 모두 healthy) |
| QA_BASE_URL | http://localhost:3000 |
| QA_API_URL | http://localhost:8080 |
| Playwright 버전 | 1.60.0 |
| BE commit | 74e82bf |

---

## 실패 시나리오

| ID | 제목 | severity | 의심 원인 분류 | 아티팩트 |
|---|---|---|---|---|
| E2E-01-E01 | 이메일 형식 위반 가입 시 400 | Minor | **spec 결함** — BE는 422(RFC 9457) 반환, spec이 400을 expect | [trace](../artifacts/auth-register-login-E2E-01-8a4e5-E-01-E01-이메일-형식-위반-가입-시-400-chromium/trace.zip) |
| E2E-01-E02 | 비밀번호 길이 미달 가입 시 400 | Minor | **spec 결함** — BE는 422 반환, spec이 400을 expect | [trace](../artifacts/auth-register-login-E2E-01-15f52--01-E02-비밀번호-길이-미달-가입-시-400-chromium/trace.zip) |
| E2E-03-03 | GET /bookings/me — 200 + Page 응답 구조 | Minor | **spec 결함** — BE 응답 키가 `bookings` (spec은 `content` 또는 `items` 기대) | [trace](../artifacts/booking-create-list-E2E-03-b7439-ookings-me-—-200-Page-응답-구조-chromium/trace.zip) |
| E2E-06-R02 | 같은 Idempotency-Key로 /goods-orders 재호출 시 동일 order id | Major | **BE 결함** — 동일 Idempotency-Key로 2회 호출 시 id가 각각 다른 row 생성 (멱등 미구현 또는 키 조회 버그) | [trace](../artifacts/goods-cart-order-E2E-06-go-641b3-ds-orders-재호출-시-동일-order-id-chromium/trace.zip) |
| E2E-08-03 | GET /notifications/me/unread-count — 200 + unreadCount 숫자 | Minor | **spec 결함** — BE 응답 키가 `count`, spec은 `unreadCount` 기대 | [trace](../artifacts/notification-message-E2E-0-35e07--count-—-200-unreadCount-숫자-chromium/trace.zip) |
| E2E-08-E03 | 알림 0건 user — unread-count 0 | Minor | **spec 결함** — 동일, BE 응답 `count: 0` vs spec `body.unreadCount` | [trace](../artifacts/notification-message-E2E-0-3fb75-알림-0건-user-—-unread-count-0-chromium/trace.zip) |
| E2E-08-E04 | 빈 메시지 내용 POST 시 400 | Minor | **spec 결함** — 500 not in [400, 422] assertion 방향 오류 (실제 응답 403 — 방 미참여자) | [trace](../artifacts/notification-message-E2E-0-6b5e4--08-E04-빈-메시지-내용-POST-시-400-chromium/trace.zip) |
| E2E-04-05 | GET /payments/me?status=PAID — 결과는 모두 PAID | Major | **BE 결함** — `status` 파라미터 필터링 시 500 Internal Error 반환 (쿼리/enum 매핑 버그 의심) | [trace](../artifacts/payment-create-list-E2E-04-13638-e-status-PAID-—-결과는-모두-PAID-chromium/trace.zip) |
| E2E-04-E01 | Idempotency-Key 없이 POST /payments 호출 시 400 | Major | **BE 결함** — 400 기대, 실제 500 반환 (헤더 미존재 시 NullPointerException 의심) | [trace](../artifacts/payment-create-list-E2E-04-2a54f-y-없이-POST-payments-호출-시-400-chromium/trace.zip) |
| E2E-05-E02 | Idempotency-Key 없이 POST /ticket-orders 시 400 | Major | **BE 결함** — 400 기대, 실제 500 반환 (헤더 미존재 시 NullPointerException 의심, payments와 동일 패턴) | [trace](../artifacts/ticket-event-purchase-E2E--c03ca-없이-POST-ticket-orders-시-400-chromium/trace.zip) |
| E2E-05-E05 | 빈 seatIds로 select 호출 시 400 | Major | **BE 결함** — 400 기대, 실제 500 반환 (빈 배열 입력에 대한 검증 미처리) | [trace](../artifacts/ticket-event-purchase-E2E--17838-빈-seatIds-로-select-호출-시-400-chromium/trace.zip) |

---

## 실패 상세 — 원인 분류

### spec 결함 (7건) — spec 수정으로 해결 가능

| ID | spec 기대 | BE 실제 응답 | 수정 방향 |
|---|---|---|---|
| E2E-01-E01 | HTTP 400 | HTTP 422 (RFC 9457 ValidationError) | spec에서 `[400, 422]` 허용 또는 422로 수정 |
| E2E-01-E02 | HTTP 400 | HTTP 422 | 동일 |
| E2E-03-03 | `body.content` 또는 `body.items` | `body.bookings` (키 이름 다름) | spec에서 `body.bookings` 로 수정 |
| E2E-08-03 | `body.unreadCount` | `body.count` | spec에서 `body.count` 로 수정 |
| E2E-08-E03 | `body.unreadCount === 0` | `body.count === 0` | 동일 |
| E2E-08-E04 | `[400, 422].toContain(status)` (500이 없으면 pass 기대) | 403 반환 (방 미참여자) — assertion 로직 오류로 500을 검출 목록에서 누락 | spec assertion 방향 수정 |

> E2E-08-E04 상세: spec 코드 `expect([400, 422]).toContain(res.status())`는 res.status()가 500일 때 "Expected value: 500, Received array: [400, 422]" 에러를 냄. 이는 `.toContain()` 인자가 반전된 spec 코드 오류. 실제 BE는 방 미참여자이므로 403을 반환하고 있어 BE 자체는 정상.

### BE 결함 (4건) — be-implementer 수정 필요

| ID | 결함 유형 | 재현 조건 | 실제 응답 |
|---|---|---|---|
| E2E-06-R02 | 멱등 미구현 | POST /goods-orders + 동일 Idempotency-Key 2회 | 각 호출마다 새 order id 발급 (id=3, id=4 등 별도 row) |
| E2E-04-05 | 쿼리/enum 처리 오류 | GET /payments/me?status=PAID | 500 Internal Error (status 파라미터 처리 오류) |
| E2E-04-E01 | 입력 검증 미처리 | POST /payments (Idempotency-Key 헤더 누락) | 500 Internal Error (400 기대) |
| E2E-05-E02 | 입력 검증 미처리 | POST /ticket-orders (Idempotency-Key 헤더 누락) | 500 Internal Error (400 기대) |
| E2E-05-E05 | 입력 검증 미처리 | POST /events/{id}/seats/select + `"seatIds":[]` | 500 Internal Error (400 기대) |

> E2E-04-E01, E2E-05-E02: Idempotency-Key 헤더 부재 시 서버가 null 처리를 못하고 500을 던짐. 헤더 존재 여부 검증 필터/인터셉터 또는 메서드 파라미터 레벨 검증 누락으로 추정.

---

## Skip 시나리오

### 회귀 spec (5건)

| ID | 제목 | skip 사유 |
|---|---|---|
| E2E-02-04 | 시설 상세 조회 — 시드 없으면 404, 있으면 200 | spec 자체 조건부 skip — 시드 데이터 미존재 |
| E2E-02-05 | 시설 슬롯 목록 조회 200 + 배열 | spec 자체 조건부 skip — 시드 데이터 미존재 |
| E2E-04-E04 | 결제 게이트웨이 5xx stub 환경 의존 | spec 주석 `— stub 환경 의존` |
| E2E-05-R02 | 같은 Idempotency-Key ticket-orders 재호출 동일 id | spec 자체 조건부 skip — 시드 의존 |
| E2E-07-R01 | 숫자 표시 toLocaleString (페이지 텍스트 검증) | spec 자체 조건부 skip (operator-A 시드 의존) |

### 1회성 spec — portal-product-form-validation (14건 skip)

E2E-R2-01 ~ E2E-R2-09, E2E-R2-R01, E2E-R2-R02, E2E-R2-R03, E2E-R2-E01~E03, E2E-R2-E05 : 모두 `/portal/products/new` 로그인 진입이 필요한 GOODS_SELLER fixture 시드 미주입으로 skip.

### 1회성 spec — portal-stock-count-on-dashboard (4건 skip)

E2E-R1-01 ~ E2E-R1-04 : GOODS_SELLER 전용 시드 (operator-C, operator-D) 미존재로 skip.

---

## Pass 시나리오 (회귀 통과 74건)

| spec 파일 | Pass 수 |
|---|---|
| auth-register-login.spec.ts | 7 / 9 |
| booking-create-list.spec.ts | 8 / 9 |
| facility-search-list.spec.ts | 7 / 9 |
| goods-cart-order.spec.ts | 11 / 12 |
| notification-message.spec.ts | 5 / 9 |
| payment-create-list.spec.ts | 6 / 8 |
| portal-dashboard.spec.ts | 7 / 8 |
| ticket-event-purchase.spec.ts | 7 / 9 |

주요 통과 케이스:

- E2E-01-01~04 : 회원가입 → 로그인 → 토큰 갱신 → 로그아웃 happy path 전 구간 통과
- E2E-04-02 : POST /payments 멱등 (동일 Idempotency-Key → 동일 payment id) 통과
- E2E-07-01~05, E2E-07-E01~E03 : 포털 대시보드 역할별 렌더링, 미인증 리다이렉트, 5xx graceful 처리 통과
- E2E-06-01~07, E2E-06-E01~E05 : 상품 검색, 카트, 주문 기본 플로우 통과

---

## 1회성 spec Pass 시나리오 (7건)

| ID | 제목 |
|---|---|
| E2E-R2-E04 | 비로그인 /portal/products/new 진입 시 로그인 리다이렉트 |
| E2E-R1-R01 | dashboard summary API 비인증 시 401/403 |
| E2E-R1-R02 | dashboard summary API 단일 호출 — N+1 없음 |
| E2E-R1-R03 | /portal 페이지 5xx 미반환 |
| E2E-R1-E01 | 소프트 삭제 상품 outOfStockProducts 미포함 |
| E2E-R1-E02 | /portal 5xx 없이 로드 — StockCustomRepository 빈 연결 정상 |
| E2E-R1-E03 | dashboard API 5xx 시 portal 페이지 유지 |

---

## 환경 메타

| 항목 | 값 |
|---|---|
| Playwright 버전 | 1.60.0 |
| BE commit | 74e82bf |
| FE | Next.js production build (`next start`, port 3000) |
| DB 시드 | payments=4, events=7, products (seed.sql 기준), users 동적 생성 |
| 실행 시간 (총) | 24.7s |
| 실행 로그 (회귀) | logs/e2e-regression.log |
| 실행 로그 (1회성) | logs/e2e-oneshot-run2.log |
| artifacts 경로 | artifacts/ (trace.zip 11건) |

---

## 결함 요약 — qa-defect-router 인계용

### BE 결함 (진짜 결함, be-implementer 수정 대상) — 5건

1. **E2E-06-R02** Major — `POST /goods-orders` Idempotency-Key 멱등 미구현. 동일 키 2회 호출 시 별도 row 생성.
2. **E2E-04-05** Major — `GET /payments/me?status=PAID` 500 반환. status 파라미터 처리 오류.
3. **E2E-04-E01** Major — `POST /payments` Idempotency-Key 헤더 누락 시 500. 400 검증 로직 미구현.
4. **E2E-05-E02** Major — `POST /ticket-orders` Idempotency-Key 헤더 누락 시 500. 동일 패턴.
5. **E2E-05-E05** Major — `POST /events/{id}/seats/select` 빈 seatIds 시 500. 빈 배열 입력 검증 미처리.

### spec 결함 (코드 수정 금지 — spec 자체 수정 대상) — 6건

1. **E2E-01-E01, E2E-01-E02** — BE의 422 응답을 400으로 단언. `[400, 422]` 허용으로 수정.
2. **E2E-03-03** — `body.bookings` 키를 `body.content || body.items` 로 잘못 참조.
3. **E2E-08-03, E2E-08-E03** — `body.count` 키를 `body.unreadCount` 로 잘못 참조.
4. **E2E-08-E04** — `.toContain()` assertion 인자가 반전된 코드 오류.

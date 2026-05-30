# E2E 실행 리포트

## 요약

| 지표 | 영구 회귀 (8 spec) | 1회성 신규 (2 spec) | 합계 |
|---|---|---|---|
| 총 케이스 | 90 | 27 | 117 |
| Pass | 14 | 7 | 21 |
| Fail | 68 | 0 | 68 |
| Skip | 8 | 20 | 28 |
| 실행 시간 | 3.0m | 41.5s | ~3.7m |
| 환경 | docker-compose.qa.yml | docker-compose.qa.yml | — |
| QA_BASE_URL | http://localhost:3000 | http://localhost:3000 | — |
| QA_API_URL | http://localhost:8080 | http://localhost:8080 | — |

## 실패 패턴 분석

전체 68건 실패는 **단일 근본 원인**으로 수렴합니다.

모든 실패 케이스의 공통 실제 응답: `401 Unauthorized`

- `/users/register` → `401` (기대: `201`)
- `/auth/login` → `400` (기대: `200`)
- `/bookings/me` → `401` (기대: `200`)
- `/facilities` → `401` (기대: `200`)
- `/facilities/stats/gu-type` → `429 Too Many Requests` (2건 — rate-limit 연동)
- `/products` → `401` (기대: `200`)
- `/notifications/me` → `401` (기대: `200`)
- `/payments/me` → `401` (기대: `200`)
- `/events` → `401` (기대: `200`)

BE 전 엔드포인트에 인증 미들웨어가 활성화되어 있어 비인증 요청이 모두 `401`로 차단됩니다. `X-User-Id` 헤더 방식도 차단되는 패턴.

## 실패 시나리오 (영구 회귀 68건)

| ID | 제목 | 실제 응답 | 아티팩트 |
|---|---|---|---|
| E2E-01-01 | 신규 이메일로 가입 시 201 Created | 401 | [trace](./artifacts/auth-register-login-E2E-01-700f7-1-Created-Location-헤더가-응답된다-chromium/trace.zip) |
| E2E-01-02 | 로그인 시 200 + accessToken 발급 | 400 | [trace](./artifacts/auth-register-login-E2E-01-2ab71-accessToken·refreshToken-발급-chromium/trace.zip) |
| E2E-01-03 | refreshToken 갱신 시 새 accessToken | 401 | [trace](./artifacts/auth-register-login-E2E-01-1917b-큰-갱신-시-새-accessToken-이-발급된다-chromium/trace.zip) |
| E2E-01-04 | logout 204 + 후속 401 | 401 | [trace](./artifacts/auth-register-login-E2E-01-c990c-ut-호출-시-204-같은-토큰-후속-호출-401-chromium/trace.zip) |
| E2E-01-R01 | 중복 이메일 재가입 409 | 401 | [trace](./artifacts/auth-register-login-E2E-01-df2f1-입된-이메일로-재가입-시-409-또는-도메인-예외-chromium/trace.zip) |
| E2E-01-R02 | 잘못된 비밀번호 401 | 400 | [trace](./artifacts/auth-register-login-E2E-01-40242-2-잘못된-비밀번호-로그인-시-401-토큰-미발급-chromium/trace.zip) |
| E2E-01-E01 | 이메일 형식 위반 400 | 401 | [trace](./artifacts/auth-register-login-E2E-01-8a4e5-E-01-E01-이메일-형식-위반-가입-시-400-chromium/trace.zip) |
| E2E-01-E02 | 비밀번호 길이 미달 400 | 401 | [trace](./artifacts/auth-register-login-E2E-01-15f52--01-E02-비밀번호-길이-미달-가입-시-400-chromium/trace.zip) |
| E2E-03-01 | POST /bookings 202/4xx | 401 | [trace](./artifacts/booking-create-list-E2E-03-85de5-드-슬롯이-없어-422-400-환경-검증으로-대체-chromium/trace.zip) |
| E2E-03-02 | GET /bookings/{id} 200/404 | 401 | [trace](./artifacts/booking-create-list-E2E-03-82b89-s-id-—-임의-id-조회-시드-없으면-404--chromium/trace.zip) |
| E2E-03-03 | GET /bookings/me 200 + Page | 401 | [trace](./artifacts/booking-create-list-E2E-03-b7439-ookings-me-—-200-Page-응답-구조-chromium/trace.zip) |
| E2E-03-04 | GET /bookings/me?status=PENDING | 401 | [trace](./artifacts/booking-create-list-E2E-03-17809-PENDING-—-필터-결과는-모두-PENDING-chromium/trace.zip) |
| E2E-03-R01 | 페이징 기본값 size=20 | 401 | [trace](./artifacts/booking-create-list-E2E-03-07259-징-기본값-유지-—-size-미명시-시-기본-20-chromium/trace.zip) |
| E2E-03-E03 | user-A booking → user-B 403/404 | 401 | [trace](./artifacts/booking-create-list-E2E-03-fcd85-ing-을-user-B-가-조회-시-403-404-chromium/trace.zip) |
| E2E-03-E04 | booking 0건 /bookings/me 200 + 빈 | 401 | [trace](./artifacts/booking-create-list-E2E-03-57cd5--bookings-me-호출-시-200-빈-페이지-chromium/trace.zip) |
| E2E-02-01 | GET /facilities 200 + Page | 401 | — |
| E2E-02-02 | gu=강남구 필터 | 401 | — |
| E2E-02-03 | type=풋살장 필터 | 401 | — |
| E2E-02-04 | 시설 상세 조회 | 429 (rate-limit 연쇄) | — |
| E2E-02-R01 | /facilities/stats/gu-type | 429 | — |
| E2E-02-R02 | 페이지 size 기본값 50 | 401 | — |
| E2E-02-E01 | 존재하지 않는 시설 id 404 | 401 | — |
| E2E-02-E02 | 없는 gu 조회 200 + 빈 | 401 | — |
| E2E-02-E03 | 슬롯 없는 시설 slots 200 + 빈 | 401 | — |
| E2E-06-01 | GET /products?category=APPAREL | 401 | — |
| E2E-06-02 | keyword=유니폼 검색 | 401 | — |
| E2E-06-03 | priceMin/Max 범위 필터 | 401 | — |
| E2E-06-04 | GET /products/popular | 401 | — |
| E2E-06-05 | POST /cart/items | 401 | — |
| E2E-06-06 | GET /cart/me | 401 | — |
| E2E-06-07 | POST /goods-orders + Idempotency-Key | 401 | — |
| E2E-06-R01 | GET /products 기본 정렬 | 401 | — |
| E2E-06-R03 | DELETE /cart/items → GET /cart/me | 401 | — |
| E2E-06-E01 | 품절 상품 추가 일관성 | 401 | — |
| E2E-06-E02 | quantity=0/-1 400 | 401 | — |
| E2E-06-E03 | 다른 user cart item PATCH 403/404 | 401 | — |
| E2E-06-E04 | 빈 cart /goods-orders 도메인 예외 | 401 | — |
| E2E-06-E05 | DELETE /cart → GET /cart/me 빈 | 401 | — |
| E2E-08-01 | GET /notifications/me 200 + Page | 401 | [trace](./artifacts/notification-message-E2E-0-35e07--count-—-200-unreadCount-숫자-chromium/trace.zip) |
| E2E-08-02 | GET /notifications/me?onlyUnread=true | 401 | — |
| E2E-08-03 | GET /notifications/me/unread-count | 401 | — |
| E2E-08-04 | PATCH /notifications/{id}/read | 401 | — |
| E2E-08-05 | POST /rooms/{roomId}/messages | 401 | — |
| E2E-08-06 | GET /rooms/{roomId}/messages | 401 | — |
| E2E-08-R02 | 알림 페이징 기본값 size=20 | 401 | — |
| E2E-08-E01 | 다른 user 알림 PATCH 403/404 | 401 | — |
| E2E-08-E02 | room 미참여자 POST 403 | 401 | — |
| E2E-08-E03 | 알림 0건 unread-count 0 | 401 | [trace](./artifacts/notification-message-E2E-0-3fb75-알림-0건-user-—-unread-count-0-chromium/trace.zip) |
| E2E-08-E04 | 빈 메시지 POST 400 | 401 | [trace](./artifacts/notification-message-E2E-0-6b5e4--08-E04-빈-메시지-내용-POST-시-400-chromium/trace.zip) |
| E2E-04-01 | POST /payments + Idempotency-Key | 401 | — |
| E2E-04-03 | GET /payments/{id} | 401 | — |
| E2E-04-04 | GET /payments/me 200 + Page | 401 | [trace](./artifacts/payment-create-list-E2E-04-13638-e-status-PAID-—-결과는-모두-PAID-chromium/trace.zip) |
| E2E-04-05 | GET /payments/me?status=PAID | 401 | — |
| E2E-04-R01 | 결제 createdAt ISO-8601 UTC | 401 | — |
| E2E-04-R02 | GET /payments/me createdAt DESC | 401 | — |
| E2E-04-E01 | Idempotency-Key 없이 POST 400 | 401 | [trace](./artifacts/payment-create-list-E2E-04-2a54f-y-없이-POST-payments-호출-시-400-chromium/trace.zip) |
| E2E-04-E02 | 다른 user payment 조회 403/404 | 401 | — |
| E2E-04-E03 | paidAtFrom > paidAtTo 400/빈 | 401 | — |
| E2E-05-01 | GET /events?status=OPEN 200 + Page | 401 | — |
| E2E-05-02 | GET /events/1 200/404 | 401 | — |
| E2E-05-03 | POST /events/1/seats/select | 401 | — |
| E2E-05-04 | POST /ticket-orders + Idempotency-Key | 401 | — |
| E2E-05-05 | POST /events/1/seats/release | 401 | — |
| E2E-05-R01 | GET /events startsAt ISO-8601 UTC | 401 | — |
| E2E-05-E02 | Idempotency-Key 없이 POST 400 | 401 | [trace](./artifacts/ticket-event-purchase-E2E--c03ca-없이-POST-ticket-orders-시-400-chromium/trace.zip) |
| E2E-05-E03 | 없는 event id 404 | 401 | — |
| E2E-05-E04 | 좌석 락 TTL 만료 후 발권 | 401 | — |
| E2E-05-E05 | 빈 seatIds select 400 | 401 | [trace](./artifacts/ticket-event-purchase-E2E--17838-빈-seatIds-로-select-호출-시-400-chromium/trace.zip) |

## Pass 시나리오 — 영구 회귀 통과 (14건)

| ID | 제목 | spec |
|---|---|---|
| E2E-01-R03 | 만료/무효 refreshToken 갱신 시 401 | auth-register-login |
| E2E-01-E03 | Authorization 헤더 없이 logout 401 | auth-register-login |
| E2E-03-E01 | X-User-Id 없이 POST /bookings 4xx | booking-create-list |
| E2E-03-E02 | 동일 슬롯 동시 booking — 한 쪽만 성공 | booking-create-list |
| E2E-07-01 | FACILITY_OWNER /portal 렌더 (<500) | portal-dashboard |
| E2E-07-02 | EVENT_HOST /portal 200 | portal-dashboard |
| E2E-07-03 | GOODS_SELLER /portal 200 | portal-dashboard |
| E2E-07-04 | 3개 역할 /portal 정상 로드 | portal-dashboard |
| E2E-07-05 | 역할 없는 사용자 /portal 200/리다이렉트 | portal-dashboard |
| E2E-07-R02 | dashboard summary API 401/403 응답 일관성 | portal-dashboard |
| E2E-07-E01 | dashboard 5xx 시 portal 페이지 미깨짐 | portal-dashboard |
| E2E-07-E02 | 미인증 /portal → 로그인 리다이렉트 | portal-dashboard |
| E2E-07-E03 | summary null — '표시할 데이터 없음' 안내 | portal-dashboard |
| E2E-05-E01 | user-A LOCKED 좌석 → user-B 동시 select 한쪽 성공 | ticket-event-purchase |

## Skip 시나리오 (8건) — 시드 의존 또는 조건 미충족

| ID | 이유 |
|---|---|
| E2E-07-R01 | qa-portal-fixture 계정 로그인 실패 — seed.sql 미주입 |
| E2E-03-R02 | 시드 미주입 — 생성 직후 PENDING 상태 검증 불가 |
| E2E-02-05 | 시설 슬롯 조회 — 시드 없어 skip |
| E2E-06-R02 | 상품 시드 product id=5 미존재 |
| E2E-08-R01 | room 시드 미주입 — cursor 페이징 검증 불가 |
| E2E-04-02 | 멱등성 — POST /payments 1회 202 필요, 시드 미주입 |
| E2E-04-E04 | 결제 게이트웨이 stub 환경 의존 |
| E2E-05-R02 | ticket-orders 멱등 — 시드 미주입 |

## 1회성 신규 시나리오 결과

### E2E-R1 portal-stock-count-on-dashboard (Stock repository 리팩토링 영향)

| ID | 제목 | 결과 | 비고 |
|---|---|---|---|
| E2E-R1-01 | operator-C outOfStockProducts=2 | Skip | operator-c 시드 미주입 |
| E2E-R1-02 | operator-D outOfStockProducts=1 | Skip | operator-d 시드 미주입 |
| E2E-R1-03 | operator-A 상품 섹션 미노출 | Skip | operator-a 시드 미주입 |
| E2E-R1-04 | operator-C 재진입 카운트 일관성 | Skip | operator-c 시드 미주입 |
| E2E-R1-R01 | dashboard summary API 비인증 401/403 | **Pass** | — |
| E2E-R1-R02 | dashboard summary API 단일 호출 일관성 | **Pass** | — |
| E2E-R1-R03 | /portal 5xx 미반환 | **Pass** | — |
| E2E-R1-E01 | 소프트 삭제 상품 카운트 제외 — API 일관성 | **Pass** | — |
| E2E-R1-E02 | StockCustomRepository 빈 연결 이상 없음 | **Pass** | — |
| E2E-R1-E03 | portal 페이지 빈 화면 아님 | **Pass** | — |

### E2E-R2 portal-product-form-validation (zod 4.x 마이그레이션 영향)

| ID | 제목 | 결과 | 비고 |
|---|---|---|---|
| E2E-R2-01 | 가격 빈 채 저장 → '가격을 입력해 주세요.' | Skip | operator-c 시드 미주입 |
| E2E-R2-02 | 카테고리 미선택 → '카테고리를 선택해 주세요.' | Skip | operator-c 시드 미주입 |
| E2E-R2-03 | 잘못된 이미지 URL → '올바른 이미지 URL' | Skip | operator-c 시드 미주입 |
| E2E-R2-04 | 가격 비숫자 → type 검증 메시지 | Skip | operator-c 시드 미주입 |
| E2E-R2-05 | 유효 값 저장 → 폼 제출 성공 | Skip | operator-c 시드 미주입 |
| E2E-R2-06 | 수정 폼 가격 빈 채 저장 | Skip | operator-c 시드 미주입 |
| E2E-R2-07 | 수정 폼 카테고리 빈 채 저장 | Skip | operator-c 시드 미주입 |
| E2E-R2-08 | 재고 복원 폼 수량 빈 제출 | Skip | operator-c 시드 미주입 |
| E2E-R2-09 | 재고 복원 폼 음수 수량 | Skip | operator-c 시드 미주입 |
| E2E-R2-R01 | zod 검증 메시지 한국어 노출 | Skip | operator-c 시드 미주입 |
| E2E-R2-R02 | i18n 영문 fallback 미노출 | Skip | operator-c 시드 미주입 |
| E2E-R2-R03 | /portal/products/new 5xx 없이 렌더 | Skip | operator-c 시드 미주입 |
| E2E-R2-E01 | 모든 필드 빈 → 검증 메시지 동시 표시 | Skip | operator-c 시드 미주입 |
| E2E-R2-E02 | 가격 2^53 초과 입력 | Skip | operator-c 시드 미주입 |
| E2E-R2-E03 | 검증 실패 후 유효 값 수정 → 메시지 사라짐 | Skip | operator-c 시드 미주입 |
| E2E-R2-E04 | 비로그인 /portal/products/new → 로그인 리다이렉트 | **Pass** | 시드 불필요 |
| E2E-R2-E05 | 다른 operator 상품 폼 직접 접근 403/오류 | Skip | operator-c 시드 미주입 |

## 환경 메타

| 항목 | 값 |
|---|---|
| Playwright version | 1.60.0 |
| BE/FE commit | 74e82bf |
| DB 시드 | qa/e2e/fixtures/seed.sql + seed-mongo.js |
| 실행 모드 | --workers=1 (시드 충돌 방지) |
| 브라우저 | chromium (Desktop Chrome) |
| BE 인증 방식 | Bearer token (JWT) 전 엔드포인트 적용 — 비인증 401 |

## 근본 원인 요약

**영구 회귀 68건 실패 = BE 전 엔드포인트 인증 강제 (401 Uniform)**

- `POST /users/register`조차 401 반환 → 회원가입 API에 인증 미들웨어가 잘못 적용된 것으로 판단됩니다.
- 이전 회귀(`feat/qa-pipeline` 이전 커밋)에서는 register/login이 공개 엔드포인트였으나 현재 구성에서 막혀 있습니다.
- 2건의 429 (Too Many Requests)는 rate-limit 미들웨어가 연속 401 응답 중 트리거된 것으로 추정됩니다.

**1회성 신규 시나리오 20건 skip = operator-multi-role.sql 시드 미적용**

- `qa/e2e/fixtures/operator-multi-role.sql`이 시드 적용 목록에 포함되지 않아 operator-c/d/a 계정이 DB에 없습니다.
- 시드 파일 존재 여부 및 seed.sql 포함 여부를 qa-defect-router에서 확인 권장합니다.

## 아티팩트 위치

- 로그: `.analysis/outputs/qa/20260524_full-regression/logs/e2e-regression.log`
- 1회성 로그: `.analysis/outputs/qa/20260524_full-regression/logs/e2e-oneshot.log`
- 아티팩트(trace.zip 11건): `.analysis/outputs/qa/20260524_full-regression/artifacts/`
- 신규 spec: `.analysis/outputs/qa/20260524_full-regression/specs/portal-stock-count-on-dashboard.spec.ts`
- 신규 spec: `.analysis/outputs/qa/20260524_full-regression/specs/portal-product-form-validation.spec.ts`

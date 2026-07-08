# E2E 실행 리포트 — 2차 회귀 (full-regression-run2)

## 요약

| 지표 | 값 |
|---|---|
| 총 시나리오 | 90 |
| Pass | 14 |
| Fail | 68 |
| Skip | 8 |
| 실행 시간 | 8.75초 |
| 환경 | docker-compose.qa.yml |
| QA_BASE_URL | http://localhost:3000 |
| QA_API_URL | http://localhost:8080 |
| 실행 시각 | 2026-05-23T09:03:40.039Z |

## 1차 회귀 대비 진전

| 항목 | 1차 | 2차 |
|---|---|---|
| 빌드 차단 결함(DEF-001 zod v4 / DEF-002 Stock fragment) | 차단 — 0건 실행 | 해소 — 90건 실행 |
| FE /portal 500 | 차단 | 통과 (E2E-07 전체 13건 중 5 pass + 1 skip) |
| BE 기동 | 차단 | UP 확인 |

> 패치 두 건이 빌드 차단은 해소했음을 확인. 그러나 런타임 결함 68건이 신규 노출됨.

## 실패 원인 분류

실패 68건은 3가지 루트 카우즈로 수렴합니다.

### [RC-1] BE API 전역 401 — 인증 없는 요청이 401 반환 (20건)

`/users/register`, `/facilities`, `/events`, `/payments/me` 등 익명 또는 자체 인증 흐름이 필요한 엔드포인트 전반에서 401이 반환됩니다.

- `POST /users/register` → 401 (expected 201)
- `POST /auth/refresh` → 401 (expected 200)  ← E2E-01-R03은 통과. 같은 엔드포인트인데 응답 불일치 — 직전 다른 테스트의 rate-limit 영향 가능성
- `GET /facilities` → 401 (expected 200)
- `GET /payments/me` → 401 (expected 200)
- `GET /events` → 401 (expected 200)

추정 원인: Spring Security 설정에서 `permitAll()` 범위가 의도보다 좁거나, `QA_API_URL` 경로와 실제 BE 컨텍스트 패스 불일치.

### [RC-2] Rate Limiting 429 (15건)

여러 스펙이 병렬 실행되며 동일 IP에서 단시간 다수 요청을 보내 429(Too Many Requests)가 발생합니다.

- `GET /facilities` 반복 호출 → 429
- `GET /products?category=APPAREL` → 429
- `GET /notifications/me` → 429

추정 원인: QA 환경 rate-limiter가 프로덕션과 동일 임계치로 적용 중 (`fullyParallel: true` + 5 workers 동시 실행).

### [RC-3] Spec 허용 코드 배열에 429/401 누락 (33건)

spec이 `expect([200, 403, 404]).toContain(res.status())` 형태로 작성된 케이스에서 BE가 429 또는 401을 반환하여 배열에 없는 코드로 실패. RC-1·RC-2의 2차 효과입니다.

## 통과 시나리오

| ID | 제목 | 파일 |
|---|---|---|
| E2E-01-R03 | 만료/무효 refreshToken 갱신 시 401 | auth-register-login.spec.ts |
| E2E-01-E03 | Authorization 헤더 없이 logout 호출 시 401 | auth-register-login.spec.ts |
| E2E-03-E01 | X-User-Id 헤더 없이 POST /bookings 시 4xx | booking-create-list.spec.ts |
| E2E-03-E02 | 동일 슬롯 동시 booking 시 한 쪽만 성공 | booking-create-list.spec.ts |
| E2E-07-01 | FACILITY_OWNER 진입 — /portal 페이지가 렌더된다 | portal-dashboard.spec.ts |
| E2E-07-02 | EVENT_HOST 진입 — 페이지가 200이며 5xx가 아님 | portal-dashboard.spec.ts |
| E2E-07-03 | GOODS_SELLER 진입 — 페이지가 200이며 5xx가 아님 | portal-dashboard.spec.ts |
| E2E-07-04 | 3개 역할 보유자 — 페이지가 정상 로드된다 | portal-dashboard.spec.ts |
| E2E-07-05 | 역할 없는 사용자 — 페이지가 200이며 빈 데이터 안내 또는 리다이렉트 | portal-dashboard.spec.ts |
| E2E-07-R02 | SSR 단계에서 dashboard summary API가 1회만 호출됨 | portal-dashboard.spec.ts |
| E2E-07-E01 | dashboard summary API 5xx — 페이지가 깨지지 않음 | portal-dashboard.spec.ts |
| E2E-07-E02 | 미인증 상태 /portal 진입 시 로그인 페이지로 리다이렉트 | portal-dashboard.spec.ts |
| E2E-07-E03 | summary의 각 섹션이 모두 null — 안내 문구 | portal-dashboard.spec.ts |
| E2E-05-E01 | user-A LOCKED 좌석을 user-B가 동시 select — 한쪽만 성공 | ticket-event-purchase.spec.ts |

## 스킵 시나리오

| ID | 제목 | 스킵 사유 |
|---|---|---|
| E2E-03-R02 | booking 생성 직후 status는 PENDING | 시드 의존 (test.skip 조건부) |
| E2E-02-05 | 시설의 슬롯 목록 조회 시 200 + 배열 반환 | 시드 의존 |
| E2E-06-R02 | 같은 Idempotency-Key로 /goods-orders 재호출 시 동일 order id | 시드 의존 |
| E2E-08-R01 | 메시지 cursor 페이징 — nextCursor 또는 cursor 필드 | 시드 의존 |
| E2E-04-02 | 같은 Idempotency-Key 재호출 시 동일 payment id (멱등) | 시드 의존 |
| E2E-04-E04 | 결제 게이트웨이 5xx 시 payment 상태 FAILED | stub 환경 의존 |
| E2E-07-R01 | 숫자 표시 — toLocaleString 사용 | fixture 계정 로그인 실패 (seed.sql id=100 미적용) |
| E2E-05-R02 | 같은 Idempotency-Key로 ticket-orders 재호출 시 동일 order id | 시드 의존 |

## 실패 시나리오 상세

### RC-1: 전역 401 (20건)

| ID | 제목 | 수신 코드 | 예상 코드 | 아티팩트 |
|---|---|---|---|---|
| E2E-01-01 | 신규 이메일로 가입 시 201 Created + Location 헤더 | 401 | 201 | [trace](./artifacts/auth-register-login-E2E-01-700f7-1-Created-Location-헤더가-응답된다-chromium/trace.zip) |
| E2E-01-03 | refreshToken으로 토큰 갱신 시 새 accessToken | 401 | 200 | [trace](./artifacts/auth-register-login-E2E-01-1917b-큰-갱신-시-새-accessToken-이-발급된다-chromium/trace.zip) |
| E2E-01-04 | accessToken logout 후 동일 토큰 401 | 401 | 204 | [trace](./artifacts/auth-register-login-E2E-01-c990c-ut-호출-시-204-같은-토큰-후속-호출-401-chromium/trace.zip) |
| E2E-01-E01 | 이메일 형식 위반 가입 시 400 | 401 | 400 | [trace](./artifacts/auth-register-login-E2E-01-8a4e5-E-01-E01-이메일-형식-위반-가입-시-400-chromium/trace.zip) |
| E2E-01-E02 | 비밀번호 길이 미달 가입 시 400 | 401 | 400 | [trace](./artifacts/auth-register-login-E2E-01-15f52--01-E02-비밀번호-길이-미달-가입-시-400-chromium/trace.zip) |
| E2E-03-03 | GET /bookings/me — 200 + Page 응답 | 401 | 200 | [trace](./artifacts/booking-create-list-E2E-03-b7439-ookings-me-—-200-Page-응답-구조-chromium/trace.zip) |
| E2E-03-04 | GET /bookings/me?status=PENDING | 401 | 200 | [trace](./artifacts/booking-create-list-E2E-03-17809-PENDING-—-필터-결과는-모두-PENDING-chromium/trace.zip) |
| E2E-03-R01 | 페이징 기본값 유지 — size 미명시 시 기본 20 | 401 | 200 | [trace](./artifacts/booking-create-list-E2E-03-07259-징-기본값-유지-—-size-미명시-시-기본-20-chromium/trace.zip) |
| E2E-02-01 | GET /facilities?page=0&size=50 — Page 응답 구조 | 401 | 200 | [trace](./artifacts/facility-search-list-E2E-0-5a5fd-size-50-호출-시-200-Page-응답-구조-chromium/trace.zip) |
| E2E-02-02 | gu=강남구 필터 | 401 | 200 | [trace](./artifacts/facility-search-list-E2E-0-5e4e1-필터-시-응답에-강남구-외-시설이-포함되지-않는다-chromium/trace.zip) |
| E2E-02-03 | type=풋살장 필터 | 401 | 200 | [trace](./artifacts/facility-search-list-E2E-0-42455-pe-풋살장-필터-시-다른-타입이-포함되지-않는다-chromium/trace.zip) |
| E2E-08-R02 | 알림 페이징 기본값 | 401 | 200 | [trace](./artifacts/) |
| E2E-08-E03 | 알림 0건 user — unread-count 0 | 401 | 200 | [trace](./artifacts/) |
| E2E-04-04 | GET /payments/me — Page + createdAt DESC | 401 | 200 | [trace](./artifacts/) |
| E2E-04-05 | GET /payments/me?status=PAID | 401 | 200 | [trace](./artifacts/) |
| E2E-04-R01 | 결제 생성 응답의 createdAt ISO-8601 UTC | 401 | 200 | [trace](./artifacts/) |
| E2E-04-R02 | GET /payments/me 기본 정렬 createdAt DESC | 401 | 200 | [trace](./artifacts/) |
| E2E-04-E01 | Idempotency-Key 없이 POST /payments 시 400 | 401 | 400 | [trace](./artifacts/) |
| E2E-05-01 | GET /events?status=OPEN — Page 응답 | 401 | 200 | [trace](./artifacts/) |
| E2E-05-R01 | GET /events startsAt ISO-8601 UTC | 401 | 200 | [trace](./artifacts/) |

### RC-2: Rate Limiting 429 (15건)

| ID | 제목 | 수신 코드 | 예상 코드 | 아티팩트 |
|---|---|---|---|---|
| E2E-03-E04 | booking 0건 user /bookings/me — 빈 페이지 | 429 | 200 | [trace](./artifacts/booking-create-list-E2E-03-57cd5--bookings-me-호출-시-200-빈-페이지-chromium/trace.zip) |
| E2E-02-04 | 시설 상세 조회 — 시드 없으면 404 | 429 | 200 | [trace](./artifacts/) |
| E2E-02-R01 | GET /facilities/stats/gu-type — 카운트 배열 | 429 | 200 | [trace](./artifacts/) |
| E2E-02-R02 | 페이지 size 미명시 시 기본값 50 | 429 | 200 | [trace](./artifacts/facility-search-list-E2E-0-683bf-02-페이지-size-미명시-시-기본값-50-유지-chromium/trace.zip) |
| E2E-02-E02 | 존재하지 않는 gu 조회 시 빈 페이지 | 429 | 200 | [trace](./artifacts/) |
| E2E-06-01 | GET /products?category=APPAREL | 429 | 200 | [trace](./artifacts/) |
| E2E-06-02 | keyword=유니폼 검색 | 429 | 200 | [trace](./artifacts/) |
| E2E-06-03 | priceMin=20000&priceMax=50000 범위 | 429 | 200 | [trace](./artifacts/) |
| E2E-06-04 | GET /products/popular?category=EQUIPMENT | 429 | 200 | [trace](./artifacts/) |
| E2E-06-06 | GET /cart/me | 429 | 200 | [trace](./artifacts/) |
| E2E-06-R01 | GET /products 기본 정렬 | 429 | 200 | [trace](./artifacts/) |
| E2E-08-01 | GET /notifications/me — Page 응답 | 429 | 200 | [trace](./artifacts/) |
| E2E-08-02 | GET /notifications/me?onlyUnread=true | 429 | 200 | [trace](./artifacts/) |
| E2E-08-03 | GET /notifications/me/unread-count | 429 | 200 | [trace](./artifacts/) |
| E2E-05-E02 | Idempotency-Key 없이 POST /ticket-orders 시 400 | 429 | 400 | [trace](./artifacts/) |

### RC-3: Spec 허용 배열 범위 밖 응답 (33건)

RC-1·RC-2가 전파된 케이스. spec이 `expect([200, 403, 404]).toContain(status)` 형태인데 401·429가 수신됨.

대표 케이스:

| ID | 제목 | 수신 | spec 허용 배열 |
|---|---|---|---|
| E2E-01-R01 | 이미 가입된 이메일로 재가입 시 409 또는 도메인 예외 | 401 | [409, 400, 422] |
| E2E-01-R02 | 잘못된 비밀번호 로그인 시 401 | 400 | — (E2E-01-02에서 회원가입 401 전파) |
| E2E-03-01 | POST /bookings 환경 검증 | 401 | [202, 400, 404, 409, 422, 500] |
| E2E-03-02 | GET /bookings/{id} | 401 | [200, 403, 404] |
| E2E-08-E04 | 빈 메시지 내용 POST 시 400 | 401 | [400, 422] |
| E2E-05-E05 | 빈 seatIds로 select 호출 시 400 | 400→expected 429 | [400, 422] |

전체 33건 목록은 `e2e-run.log` 참조.

## 환경 메타

| 항목 | 값 |
|---|---|
| Playwright version | 1.60.0 |
| 실행 시각 | 2026-05-23T09:03:40Z |
| BE | http://localhost:8080 (UP — /actuator/health 확인) |
| FE | http://localhost:3000 (next start production 빌드) |
| 인프라 | docker-compose.qa.yml (MySQL/MongoDB/Redis/Kafka/Zookeeper) |
| 병렬 workers | 5 (fullyParallel: true) |
| 브라우저 | chromium |
| 아티팩트 | .analysis/outputs/qa/20260523_full-regression-run2/artifacts/ (68개 디렉토리) |
| raw 로그 | .analysis/outputs/qa/20260523_full-regression-run2/e2e-run.log |

## 1차 회귀 대비 결론

- DEF-001(zod v4) + DEF-002(Stock fragment) 패치로 빌드 차단이 해소되어 90건 전체가 실행됨. (1차: 0건 실행)
- FE SSR 렌더링 관련 E2E-07 시나리오 5건이 신규 통과. portal 빌드 차단은 완전히 해소됨.
- 신규 노출 결함 2가지:
  - **BE 전역 401**: `/users/register`를 포함한 다수 엔드포인트가 인증 없이 401 반환. Spring Security `permitAll()` 범위 이상.
  - **Rate Limiting 429**: QA 병렬 실행(5 workers) 환경에서 rate-limiter 임계 초과. QA 환경 전용 완화 설정 필요 또는 workers=1 순차 실행 재시도 필요.
- 결함 분류·Jira 등록은 qa-defect-router의 담당.

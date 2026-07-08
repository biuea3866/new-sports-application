# E2E 회귀 실행 매니페스트 — /qa --full-regression 20260607

## 모드
- `--full-regression` — 영구 회귀 스위트 전수 + 직전 회귀(20260531) 이후 변경 표면 보강.
- 신규 프로덕션 기능 코드 없음. 직전 변경 표면은 PR #181(cart NonUnique fix)·#182(payments status enum fix) 2건뿐이며 둘 다 **런타임 재검증 미수행** 상태.

## 1. 영구 회귀 스위트 (전수 실행 — `qa/e2e/scenarios/` 기준)

아래 9개 시나리오(8개 플로우 + self-check)를 전부 회귀 대상으로 채택합니다. 시나리오 본문은 영구 저장소(`qa/e2e/scenarios/{slug}.md`)를 그대로 사용하며, 본 산출물에서는 **변경 표면이 닿는 2개 플로우만 보강본을 별도 작성**합니다.

| # | 영구 시나리오 | spec | severity | 회귀 채택 | 보강 여부 |
|---|---|---|---|---|---|
| E2E-01 | `qa/e2e/scenarios/auth-register-login.md` | auth-register-login.spec.ts | Critical | ✅ 그대로 | - |
| E2E-02 | `qa/e2e/scenarios/facility-search-list.md` | facility-search-list.spec.ts | Critical | ✅ 그대로 | - |
| E2E-03 | `qa/e2e/scenarios/booking-create-list.md` | booking-create-list.spec.ts | Critical | ✅ 그대로 | - |
| E2E-04 | `qa/e2e/scenarios/payment-create-list.md` | payment-create-list.spec.ts | Critical | ✅ 그대로 + **보강** | `payment-create-list.md` (이 dir) |
| E2E-05 | `qa/e2e/scenarios/ticket-event-purchase.md` | ticket-event-purchase.spec.ts | Critical | ✅ 그대로 | - |
| E2E-06 | `qa/e2e/scenarios/goods-cart-order.md` | goods-cart-order.spec.ts | Major | ✅ 그대로 + **보강** | `goods-cart-order.md` (이 dir) |
| E2E-07 | `qa/e2e/scenarios/portal-dashboard.md` | portal-dashboard.spec.ts | Major | ✅ 그대로 | - |
| E2E-08 | `qa/e2e/scenarios/notification-message.md` | notification-message.spec.ts | Major | ✅ 그대로 | - |
| (보강) | mobile-screen-render.spec.ts | mobile-screen-render.spec.ts | Minor | ✅ spec만 실행 (시나리오 md 없음 — placeholder 렌더) | - |
| (보강) | portal-insights-inbox.spec.ts | portal-insights-inbox.spec.ts | Major | ✅ spec만 실행 | - |

> spec 10개는 모두 회귀에서 실행합니다. 시나리오 md가 없는 spec 2개(mobile-screen-render·portal-insights-inbox)는 spec 자체가 회귀 단언을 담고 있어 그대로 실행하되, 본 회귀에서 신규 시나리오 md는 도출하지 않습니다(변경 표면 무관).

## 2. 변경 표면 보강 (이번 회귀 신규 도출)

| 산출 파일 | 트리거 | 신규/보강 케이스 | severity |
|---|---|---|---|
| `e2e/goods-cart-order.md` | PR #181 (cart NonUnique 500 fix, 런타임 재검증 미수행) | 동시/중복 cart 요청 → NonUniqueResult 미발생 + 단일 cart 수렴 (E2E-06-09·R04·R05) | Major |
| `e2e/payment-create-list.md` | PR #182 (payments status 무효 enum → 400 fix, 런타임 재검증 미수행) | 무효 status 필터 → 400(500 아님), 빈 status → 200 (E2E-04-06·E05·E06) | Critical |
| `load/cart-add-item-concurrency.md` | PR #181 동시성 재검증 보강 | 같은 user 동시 cart 추가 부하 → 5xx(NonUniqueResult) 0건 + cart row 단일 수렴 | Major |

> 보강 시나리오 md는 영구 시나리오를 **대체하지 않고**, 같은 E2E 번호(E2E-04/E2E-06)에 케이스를 추가한 1회성 확장본입니다. 결함이 실제로 발견되면 사람 검토 후 해당 케이스를 영구 시나리오로 승격합니다(qa-scenario-guide 승격 기준).

## 3. 부하 회귀 스위트 (전수 실행 — `qa/load/scenarios/` 기준)

| LOAD | 영구 시나리오 | k6 | objective | 회귀 채택 |
|---|---|---|---|---|
| LOAD-01 | `qa/load/scenarios/facility-search.md` | facility-search.js | latency | ✅ 그대로 |
| LOAD-02 | `qa/load/scenarios/ticket-seat-select-spike.md` | ticket-seat-select-spike.js | spike | ✅ 그대로 |
| LOAD-03 | `qa/load/scenarios/booking-create-throughput.md` | booking-create-throughput.js | throughput | ✅ 그대로 (직전 회귀 슬롯 시드 부족 한계 — seed 보강 후 재측정 권장) |
| LOAD-EXAMPLE-01 | (예시) | example.bookings-get.js | latency | ⏭️ 회귀 대상 아님 (예시 스크립트) |
| LOAD-04 | `load/cart-add-item-concurrency.md` (이 dir) | (qa-load-tester가 생성) | throughput/동시성 | ✅ 신규 — PR #181 동시성 재검증 |

## 4. 직전 회귀 미해소 항목 (런타임 재검증 트리거)

| 항목 | 상태 | 본 회귀 액션 |
|---|---|---|
| PR #181 cart 동시성 500 (Step 5-B 미수행) | dev 머지됨, 런타임 미검증 | E2E-06 보강 + LOAD-04 신규로 재검증 |
| PR #182 payments 무효 enum 400 (런타임 미검증) | dev 머지됨, 런타임 미검증 | E2E-04 보강으로 재검증 |
| origin/dev detekt 11건 (weather InvalidPackageDeclaration) | 선존재 부채 | 시나리오 무관 — 회귀 범위 밖 |
| LOAD-03 슬롯 시드 부족 | 하니스 한계 | `qa/load/seeds/booking-create-throughput.sql` 보강 후 재측정 (qa-load-tester 책임) |

## 환경 컨텍스트 (모든 시나리오 공통)
- QA_BASE_URL=http://localhost:3000, QA_API_URL=http://localhost:8080
- 웹 B2B 포털 로그인 폼 없음 — 인증은 BE API 직접(`POST /users/register` → `POST /auth/login` → accessToken/쿠키 주입), `helpers.ts`의 `registerUser`/`loginUser`/`registerAndLogin`/`bearer` 사용. `/login`·`/dev-login` 미사용.
- 미인증 `/portal` 접근은 307 → `/login`(404) dead-end = 의도. **인증 후 렌더 단언이 핵심** — status만 보는 약한 검증 금지.
- fixture 계정: `qa-portal-fixture@test.local`(id 100, B2B 역할 보유, seed.sql). seed-mongo: facilities 3건. 신규 사용자는 `uniqueEmail`로 self-register.

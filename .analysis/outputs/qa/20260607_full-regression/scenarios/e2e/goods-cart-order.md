# E2E-06 굿즈 검색 · 장바구니 · 주문 (회귀 보강 — cart 단일성)

> 영구 시나리오 `qa/e2e/scenarios/goods-cart-order.md`의 1회성 보강본입니다.
> 기존 E2E-06-01~07 / R01~R03 / E01~E05는 영구 시나리오 그대로 회귀 실행하고,
> 본 파일은 PR #181(cart NonUnique 500 fix, 런타임 재검증 미수행) 재검증용 신규 케이스만 추가합니다.

## 메타
- severity: Major
- layer-hint: BE
- related-files:
  - backend/src/main/kotlin/com/sportsapp/infrastructure/persistence/goods/CartRepositoryImpl.kt
  - backend/src/main/kotlin/com/sportsapp/domain/goods/CartDomainService.kt
  - backend/src/main/kotlin/com/sportsapp/domain/goods/Cart.kt
  - backend/src/main/kotlin/com/sportsapp/presentation/goods/CartApiController.kt
  - backend/src/main/resources/db/migration/V34__fix_carts_active_unique.sql
- related-ticket: none (PR #181 fix/qa-20260531-cart-nonunique)
- estimated-duration: 1m

## 사전 조건
- DB 시드: seed.sql (fixture 계정 `qa-portal-fixture@test.local` id 100). 신규 케이스는 `uniqueEmail`로 self-register한 깨끗한 user 사용 — 활성 cart 0건 상태에서 시작.
- 인증 상태: user-A (self-register 신규 계정). cart API는 `X-User-Id` 헤더 권한 모델.
- 환경 변수: 없음
- 주의: V34 이후 `UNIQUE(user_id, active_marker)` 제약이 존재. `CartRepositoryImpl.findByUserId`는 단건 read(쓰기 사이드이펙트 없음)로 복원된 상태여야 한다.

## 시나리오 (Given/When/Then 한 줄)
| ID | Given | When | Then |
|---|---|---|---|
| E2E-06-08 | 활성 cart 0건인 신규 user-A | `POST /cart/items`로 동일 상품을 **순차로 2회** 추가할 때 | 두 호출 모두 2xx이고 이후 `GET /cart/me`가 단일 cart(중복 cart 없음)를 반환한다 |
| E2E-06-09 | 활성 cart 0건인 신규 user-A | `POST /cart/items`를 같은 user로 **동시에 N(=5)회** 발사할 때 | 어느 호출도 500(NonUniqueResult)을 반환하지 않고, 직후 `GET /cart/me`가 단일 cart로 수렴한다 |

## 회귀 케이스 (변경 후에도 깨지면 안 되는 기존 동작)
| ID | 케이스 |
|---|---|
| E2E-06-R04 | (PR #181 재현) 같은 user가 cart 조회(`GET /cart/me`)를 동시에 N회 호출해도 500(NonUniqueResultException)이 0건이고 모두 200을 반환한다 |
| E2E-06-R05 | (PR #181 회귀) `GET /cart/me` 호출이 cart row 수를 변경하지 않는다 — 조회 전후 활성 cart count가 1로 동일하다 (findByUserId 쓰기 사이드이펙트 제거 확인) |
| E2E-06-R06 | 기존 단일 활성 cart user의 `GET /cart/me`는 V34 적용 후에도 동일 cart를 반환한다 (하위호환) |

## 엣지 케이스
| ID | 케이스 |
|---|---|
| E2E-06-E06 | 동시 cart 추가 경합에서 일부 요청이 패하더라도 응답은 5xx가 아닌 비즈니스 응답(2xx 또는 409)이며, DB 활성 cart row는 정확히 1건이다 |
| E2E-06-E07 | cart가 비어있지 않은 user가 `DELETE /cart` 후 다시 `POST /cart/items` 추가 시 새 활성 cart 1건이 정상 생성되고 UNIQUE 제약 위반(500)이 발생하지 않는다 |

## 보강 근거 (변경 표면 추적)
- PR #181: `CartRepositoryImpl.findByUserId`가 쓰기(saveAll) 사이드이펙트를 가져 동시 조회 시 두 번째 호출이 `softDelete()` 가드(`check(deletedAt == null)`)로 500이 발생하던 결함을 단건 read로 복원. V34가 기존 중복을 dedup하고 `UNIQUE(user_id, active_marker)`로 신규 중복을 차단.
- 직전 회귀(20260531)에서 동시 세션 충돌로 Step 5-B 통합 런타임 재검증을 못 함 → **동시/중복 요청 시 NonUniqueResult 미발생 + 단일 cart 수렴**을 본 회귀에서 런타임으로 단언.

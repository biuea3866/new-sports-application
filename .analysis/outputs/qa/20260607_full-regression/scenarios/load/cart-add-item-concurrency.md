# LOAD-04 장바구니 추가 동시성 — NonUniqueResult 미발생 검증

> PR #181(cart NonUnique 500 fix) 동시성 재검증용 신규 부하 시나리오.
> 직전 회귀(20260531)에서 동시 세션 충돌로 동시 cart 추가 런타임 재검증을 못 함 → 부하로 같은 user 동시 cart 추가를 재현.

## 메타
- target: `POST /cart/items` + `GET /cart/me` (같은 user_id에 집중)
- objective: throughput (동시성 단언 — 회귀 추세보다 5xx 0건이 핵심)
- duration: 3m (ramp-up 30s + steady 2m + ramp-down 30s)
- related-files:
  - backend/src/main/kotlin/com/sportsapp/infrastructure/persistence/goods/CartRepositoryImpl.kt
  - backend/src/main/kotlin/com/sportsapp/domain/goods/CartDomainService.kt
  - backend/src/main/kotlin/com/sportsapp/domain/goods/Cart.kt
  - backend/src/main/resources/db/migration/V34__fix_carts_active_unique.sql
- related-ticket: none (PR #181 fix/qa-20260531-cart-nonunique)

## 목표 임계
| 지표 | 목표 |
|---|---|
| RPS | 80 |
| p95 | < 500ms |
| p99 | < 1500ms |
| error rate (5xx) | **0%** (NonUniqueResult/IllegalState 500은 0건이어야 함 — 본 시나리오의 핵심 단언) |
| 자원 | CPU < 70%, Mem < 2gb |

> 주의: 일반 throughput 시나리오는 5xx<1%를 허용하나, 본 시나리오는 **동시성 결함 재현**이 목적이므로 5xx(특히 NonUniqueResultException·IllegalStateException) 0건을 임계로 둡니다. 409 같은 정상 비즈니스 경합 응답은 error로 집계하지 않습니다.

## 가상 사용자 패턴
- ramp-up: 0 → 50 VU over 30s
- steady: 50 VU for 2m
- ramp-down: 50 → 0 VU over 30s
- **경합 집중 설계**: VU를 작은 user_id 풀(예: 5명)에 매핑해 동일 user에 동시 `POST /cart/items`가 몰리도록 한다 (cart 단일성 제약을 의도적으로 압박). 1 user = 1 cart 제약 위반 시 500 발생 → 결함 신호.

## 사전 시드
- DB 시드: `qa/load/seeds/cart-add-item-concurrency.sql` (qa-load-tester 작성) — 상품(ACTIVE) 10건 + 재고 충분, cart 풀 user 5명은 활성 cart 0건 상태로 시작
- 토큰 발급: cart API는 `X-User-Id` 헤더 권한 모델 — Bearer 토큰 불필요 (`lib/auth.js#headerAuth` 사용, 인증 서버 부하 분리)
- 캐시 워밍업: 상품 목록 GET 5회 priming

## 검증
- 응답 body 검증: sampling (`__ITER % 100 === 0`) — `POST /cart/items` 2xx 응답에 cart/item 식별자 존재
- 사이드이펙트 검증 (핵심): 부하 종료 후 SQL로 **풀 user 5명 각각의 활성 cart(`deleted_at IS NULL`) row가 정확히 1건**인지 단언. 2건 이상이면 중복 cart 발생 = V34 제약 회귀.
- 5xx 응답 카운트: NonUniqueResultException·IllegalStateException 기인 500이 0건인지 raw 로그에서 확인.

## 측정 노이즈 경고
- 본 시나리오는 로컬 docker-compose 환경에서 실행되므로 **절대치(p95) 비교용이 아닌 회귀 추세 추적용**입니다.
- 다만 **5xx 0건 + 활성 cart 1건/user**는 환경 무관 불변 단언이며, 위반 시 결함 후보(운영 환경 재검증 대상)입니다.

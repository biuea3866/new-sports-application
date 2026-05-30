# E2E-06 굿즈 검색 · 장바구니 · 주문

## 메타
- severity: Major
- layer-hint: FULL-STACK
- related-files:
  - backend/src/main/kotlin/com/sportsapp/presentation/goods/ProductApiController.kt
  - backend/src/main/kotlin/com/sportsapp/presentation/goods/CartApiController.kt
  - backend/src/main/kotlin/com/sportsapp/presentation/goods/GoodsOrderApiController.kt
  - web/app/portal/products/page.tsx
  - web/app/portal/products/[id]/page.tsx
- related-ticket: none
- estimated-duration: 2m

## 사전 조건
- DB 시드: `qa/e2e/fixtures/products-multi-category.sql` (APPAREL 5건 + EQUIPMENT 5건 + 품절 1건 = 11건, 가격 1만~10만원 분포)
- 인증 상태: user-A
- 환경 변수: 없음

## 시나리오 (Given/When/Then 한 줄)
| ID | Given | When | Then |
|---|---|---|---|
| E2E-06-01 | 미인증 사용자 | `GET /products?category=APPAREL`을 호출할 때 | 200과 APPAREL 카테고리 5건이 반환된다 |
| E2E-06-02 | `keyword=유니폼` | `GET /products?keyword=유니폼`을 호출할 때 | 이름 또는 설명에 "유니폼"이 포함된 상품만 반환된다 |
| E2E-06-03 | `priceMin=20000&priceMax=50000` | `GET /products`로 가격 범위 조회 시 | 2만~5만원 구간 상품만 반환된다 |
| E2E-06-04 | `GET /products/popular?category=EQUIPMENT` | 카테고리별 인기 상품을 조회할 때 | EQUIPMENT 카테고리의 인기 상품 리스트가 반환된다 |
| E2E-06-05 | user-A + 상품 `prod-001` | `POST /cart/items`에 productId·quantity=2를 보낼 때 | 200과 cart에 item이 추가된 상태가 반환된다 |
| E2E-06-06 | user-A의 cart | `GET /cart/me`를 호출할 때 | E2E-06-05에서 추가한 item이 포함된 cart 응답이 반환된다 |
| E2E-06-07 | user-A의 cart + `Idempotency-Key: ord-001` | `POST /goods-orders`를 호출할 때 | 202 Accepted와 order id가 반환되고 cart의 해당 item은 비워진다 |

## 회귀 케이스 (변경 후에도 깨지면 안 되는 기존 동작)
| ID | 케이스 |
|---|---|
| E2E-06-R01 | `GET /products`의 기본 정렬(`sort=recent`)이 명시되지 않은 요청에서도 유지된다 |
| E2E-06-R02 | 동일 `Idempotency-Key`로 `POST /goods-orders` 재호출 시 새 주문이 생기지 않고 기존 order id가 반환된다 |
| E2E-06-R03 | `DELETE /cart/items/{itemId}` 호출 후 다시 `GET /cart/me` 시 해당 item이 사라진 cart가 반환된다 |

## 엣지 케이스
| ID | 케이스 |
|---|---|
| E2E-06-E01 | 품절 상품을 `POST /cart/items`로 추가 시도 시 도메인 예외(재고 부족)가 반환된다 |
| E2E-06-E02 | `quantity=0` 또는 음수로 cart 추가 시 400 Bad Request가 반환된다 |
| E2E-06-E03 | 다른 사용자의 cart item id로 `PATCH /cart/items/{itemId}` 호출 시 403/404가 반환된다 |
| E2E-06-E04 | cart가 비어있는 상태에서 `POST /goods-orders` 호출 시 도메인 예외가 반환된다 |
| E2E-06-E05 | `DELETE /cart` 호출 후 `GET /cart/me` 시 빈 cart가 반환된다 |

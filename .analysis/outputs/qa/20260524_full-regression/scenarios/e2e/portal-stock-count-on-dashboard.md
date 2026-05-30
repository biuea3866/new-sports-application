# E2E-R1 사업자 포털 대시보드 · 재고 0건 카운트 (Stock repository 리팩토링 영향)

## 메타
- severity: Major
- layer-hint: BE
- related-files:
  - backend/src/main/kotlin/com/sportsapp/infrastructure/persistence/goods/StockJpaRepository.kt
  - backend/src/main/kotlin/com/sportsapp/infrastructure/persistence/goods/StockRepositoryImpl.kt
  - web/app/portal/page.tsx
- related-ticket: none
- estimated-duration: 1m

## 사전 조건
- DB 시드: `qa/e2e/fixtures/operator-multi-role.sql` (기존 E2E-07과 동일 시드 재사용)
  - operator-C: GOODS_SELLER 단독 + 상품 8건 (6 ACTIVE + 2 SOLD_OUT, 즉 재고 0건 2건)
  - operator-D: 3개 역할 모두 보유 + 상품 일부 (SOLD_OUT 1건 포함)
  - operator-A: FACILITY_OWNER 단독 (상품 0건 — 재고 0건 카운트도 0이어야 함)
- 인증 상태: 각 operator로 전환하며 테스트
- 환경 변수: 없음

## 시나리오 (Given/When/Then 한 줄)
| ID | Given | When | Then |
|---|---|---|---|
| E2E-R1-01 | operator-C (GOODS_SELLER, SOLD_OUT 상품 2건) 로그인 | `/portal` 페이지 진입 시 | 상품 섹션의 outOfStockProducts 카운트가 2로 표시된다 |
| E2E-R1-02 | operator-D (3개 역할, SOLD_OUT 상품 1건) 로그인 | `/portal` 페이지 진입 시 | 상품 섹션의 outOfStockProducts 카운트가 1로 표시된다 |
| E2E-R1-03 | operator-A (FACILITY_OWNER 단독, 상품 0건) 로그인 | `/portal` 페이지 진입 시 | 상품 섹션 자체가 노출되지 않고 outOfStockProducts 라벨이 화면에 존재하지 않는다 |
| E2E-R1-04 | operator-C 로그인 + 상품 1건을 추가로 SOLD_OUT 상태로 시드 갱신 | `/portal` 재진입 시 | outOfStockProducts 카운트가 3으로 갱신되어 표시된다 |

## 회귀 케이스 (변경 후에도 깨지면 안 되는 기존 동작)
| ID | 케이스 |
|---|---|
| E2E-R1-R01 | outOfStockProducts 카운트는 본인이 소유한 상품만 집계되어야 한다 (다른 operator의 SOLD_OUT 상품은 포함되지 않는다) |
| E2E-R1-R02 | dashboard summary API는 페이지 진입 시 1회만 호출되고 Stock 분리 위임으로 인한 N+1 호출이 발생하지 않는다 |
| E2E-R1-R03 | E2E-07-03과 동일하게 totalProducts=8, activeProducts=6, outOfStockProducts=2가 동일 카운트로 노출되어 카운트 회귀 동등성을 유지한다 |

## 엣지 케이스
| ID | 케이스 |
|---|---|
| E2E-R1-E01 | 소프트 삭제된 SOLD_OUT 상품(deleted_at NOT NULL)은 outOfStockProducts 카운트에 포함되지 않는다 |
| E2E-R1-E02 | 상품이 모두 ACTIVE이고 SOLD_OUT이 0건일 때 outOfStockProducts는 0으로 정확히 표시된다 |
| E2E-R1-E03 | StockCustomRepository 빈 주입 실패 시 대시보드 API가 5xx를 반환하더라도 portal 페이지는 깨지지 않고 alert 박스가 표시된다 |

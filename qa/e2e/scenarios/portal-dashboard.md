# E2E-07 사업자 포털 대시보드 · 역할별 표시

## 메타
- severity: Major
- layer-hint: FULL-STACK
- related-files:
  - backend/src/main/kotlin/com/sportsapp/presentation/dashboard/OperatorDashboardApiController.kt
  - backend/src/main/kotlin/com/sportsapp/presentation/facility/FacilityOwnerApiController.kt
  - backend/src/main/kotlin/com/sportsapp/presentation/ticketing/EventHostApiController.kt
  - backend/src/main/kotlin/com/sportsapp/presentation/goods/GoodsSellerApiController.kt
  - web/app/portal/page.tsx
- related-ticket: none
- estimated-duration: 1m 30s

## 사전 조건
- DB 시드: `qa/e2e/fixtures/operator-multi-role.sql`
  - operator-A: FACILITY_OWNER 단독 + 시설 3건/슬롯 12건
  - operator-B: EVENT_HOST 단독 + event 5건 (3 SCHEDULED + 2 OPEN)
  - operator-C: GOODS_SELLER 단독 + 상품 8건 (6 ACTIVE + 2 SOLD_OUT)
  - operator-D: 3개 역할 모두 보유 + 위 데이터 일부씩
  - operator-E: 역할 없음 (일반 사용자)
- 인증 상태: 각 operator로 전환하며 테스트
- 환경 변수: 없음

## 시나리오 (Given/When/Then 한 줄)
| ID | Given | When | Then |
|---|---|---|---|
| E2E-07-01 | operator-A (FACILITY_OWNER) 로그인 | `/portal` 페이지 진입 시 | "시설/슬롯" 섹션이 표시되고 내 시설 수=3, 슬롯 수=12로 렌더된다 |
| E2E-07-02 | operator-B (EVENT_HOST) 로그인 | `/portal` 페이지 진입 시 | "경기" 섹션만 표시되고 totalEvents=5, scheduledEvents=3, openEvents=2로 렌더된다 |
| E2E-07-03 | operator-C (GOODS_SELLER) 로그인 | `/portal` 페이지 진입 시 | "상품" 섹션만 표시되고 totalProducts=8, activeProducts=6, outOfStockProducts=2로 렌더된다 |
| E2E-07-04 | operator-D (3개 역할) 로그인 | `/portal` 페이지 진입 시 | 시설·경기·상품 3개 섹션이 모두 표시된다 |
| E2E-07-05 | operator-E (역할 없음) 로그인 | `/portal` 페이지 진입 시 | 모든 섹션이 비어 "표시할 데이터가 없습니다." 텍스트가 표시된다 |

## 회귀 케이스 (변경 후에도 깨지면 안 되는 기존 동작)
| ID | 케이스 |
|---|---|
| E2E-07-R01 | 숫자 표시는 `toLocaleString('ko-KR')`로 천 단위 콤마가 적용된다 (예: `1,234`) |
| E2E-07-R02 | 페이지 진입 시 SSR 단계에서 dashboard summary API가 1회만 호출된다 |

## 엣지 케이스
| ID | 케이스 |
|---|---|
| E2E-07-E01 | dashboard summary API가 5xx를 반환할 때 페이지 상단에 빨간 alert 박스가 표시되고 페이지는 깨지지 않는다 |
| E2E-07-E02 | 미인증 상태로 `/portal` 진입 시 로그인 페이지로 리다이렉트된다 |
| E2E-07-E03 | summary의 각 섹션이 모두 null인 경우 "표시할 데이터가 없습니다." 안내가 렌더된다 |

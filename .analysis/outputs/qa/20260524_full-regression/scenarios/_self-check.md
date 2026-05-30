# 시나리오 산출물 자체 점검 — 20260524 full-regression

## 산출 파일 목록

| 파일 | 종류 | 상태 |
|---|---|---|
| `regression-list.md` | 회귀 목록 | 작성 완료 |
| `e2e/portal-stock-count-on-dashboard.md` | 1회성 E2E (Stock 리팩토링) | 작성 완료 |
| `e2e/portal-product-form-validation.md` | 1회성 E2E (zod 4.x) | 작성 완료 |
| `load/_none.md` | 신규 부하 없음 사유 | 작성 완료 |

## 산출 시나리오별 점검

### e2e/portal-stock-count-on-dashboard.md
- [x] severity 라벨 있음 — Major
- [x] Given/When/Then 한 줄 형식 4개 케이스
- [x] related-files 명시 — StockJpaRepository.kt, StockRepositoryImpl.kt, web/app/portal/page.tsx
- [x] Happy path (E2E-R1-01, E2E-R1-02)
- [x] 검증 실패: 본 시나리오는 카운트 회귀 검증이 목적이라 폼 검증 실패 케이스 없음. 대신 데이터 경계(상품 0건: E2E-R1-03)로 대체
- [x] 권한 실패: 회귀 케이스 R01에서 본인 소유만 집계 검증 — 권한 경계 포함
- [x] 데이터 경계 (E2E-R1-03 상품 0건, E2E-R1-E02 SOLD_OUT 0건, E2E-R1-E01 소프트 삭제)
- [x] 회귀 케이스 3건 + 엣지 케이스 3건

### e2e/portal-product-form-validation.md
- [x] severity 라벨 있음 — Major
- [x] Given/When/Then 한 줄 형식 9개 케이스
- [x] related-files 명시 — web/app/portal/products/product-form-schema.ts
- [x] Happy path (E2E-R2-05)
- [x] 검증 실패 (E2E-R2-01, 02, 03, 04, 06, 07, 08, 09)
- [x] 권한 실패 (E2E-R2-E04 비로그인 리다이렉트, E2E-R2-E05 타 operator 상품 접근)
- [x] 데이터 경계 (E2E-R2-E01 모든 필드 빈 값, E2E-R2-E02 매우 큰 값)
- [x] 회귀 케이스 3건 + 엣지 케이스 5건

## 케이스 수 검토

- portal-stock-count-on-dashboard: 메인 4 + 회귀 3 + 엣지 3 = 10건 (한 시나리오 md 10건 초과 기준 미만)
- portal-product-form-validation: 메인 9 + 회귀 3 + 엣지 5 = 17건. 신규 등록/수정/재고 복원 3개 폼 위치를 한 md에 묶었으나, zod 4.x 마이그레이션의 단일 schema 파일 영향이라 분리 시 회귀 추적이 흩어짐 → 묶음 유지

## 미충족 보완 사항

- 본 회귀 두 시나리오 모두 동시성 케이스 없음 — 두 변경 모두 동시성 특성을 바꾸지 않으므로 의도적 제외

## 회귀 시나리오 영향 매핑

| 변경 | 영향 회귀 | 회귀 시나리오 갱신 필요 여부 |
|---|---|---|
| Stock 리팩토링 | E2E-07 portal-dashboard | 카운트 동작은 동일. 1회성 시나리오로 분리 검증, 회귀 md 갱신 불필요 |
| zod 4.x 마이그레이션 | 영구 회귀에 portal 상품 폼 시나리오 없음 | 회귀 시나리오 갱신 불필요. 본 1회성 시나리오 중 일부를 향후 영구 회귀로 승격 검토 권장 |

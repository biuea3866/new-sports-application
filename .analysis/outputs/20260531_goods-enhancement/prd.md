# Goods 고도화 (재고예약·장바구니만료·리뷰·추천) PRD

> 작성일: 2026-05-31 | 작성자: biuea3866@gmail.com
> 소스: B 도메인 고도화 — 커머스 전환율·품절 손실 개선
> prd-reviewer 검수: 2026-05-31 NEEDS_REVISION → Must Fix 3건 반영 → **재검수 APPROVED** (하단 검수 이력)

---

## 진행 현황 (Status Ledger)

> 최종 갱신: 2026-05-31 | 전체: 0/5 배포완료 (0%)

| FR | 기능 | 상태 | 구현(PR) | 배포 | 비고 |
|----|------|------|---------|------|------|
| FR-00 | V34~ 신규 테이블 + restocked 토픽 (선행) | ⬜ 대기 | — | — | DDL·토픽 병목 |
| FR-01 | 재고 점유 모델 정비 + 백오더 | ⬜ 대기 | — | — | 기존 PENDING 차감 재정의 |
| FR-02 | 장바구니 만료 + 결제 시점 재검증 | ⬜ 대기 | — | — | Cart 확장 |
| FR-03 | 상품 리뷰·평점 | ⬜ 대기 | — | — | 구매이력 교차조회 선행 |
| FR-04 | 연관 추천 (함께 구매) | ⬜ 대기 | — | — | 주문 이력 배치 집계 |

### 상태 정의 (증거 기반 — `rules/COMPLETION-RULE.md` 연계)

| 상태 | 진입 조건 (증거) |
|---|---|
| ⬜ 대기 | 티켓만 존재 |
| 🟡 진행중 | 브랜치 존재 / 커밋 시작 |
| 🔵 구현완료 | dev 머지 PR + COMPLETION-RULE §1~4 충족 (테스트 통과 아티팩트) |
| ✅ 배포완료 | release 태그 / 배포 로그 첨부 |

---

## 배경 (Background)

Goods 도메인은 검색·장바구니·주문·재고 차감/보상을 갖췄습니다. AS-IS는 검수에서 코드 대조로 정정했습니다.

| 자산 (코드 확인) | 현 구조 | 한계 |
|---|---|---|
| `Stock` / `OutOfStockException` | `Stock.deduct()` `@Version` 낙관락 | — |
| `GoodsDomainService.createPendingOrder` | **PENDING 주문 생성 시점에 재고 즉시 실차감**(`validateAndDeductStock`) | 이미 단기 점유 = "예약"격 |
| 결제 실패 보상 | **consumer 아님 — `CreateGoodsOrderUseCase#processPaymentResult`의 동기 분기**(FAILED → `cancelPendingOrder` → `stock.restore`) | 비동기 보상 아님 |
| `ConfirmPaymentWebhookUseCase` | `confirmWebhook`만, **stock·GoodsOrder 미관여** | 웹훅 경로는 재고 무관 |
| `Cart` / `CartItem` | 담기 시 `validateStockSufficient` 검증 | **만료 없음**, 결제 시점 재검증 없음 |
| `GoodsOrder` / `GoodsOrderItem` | 주문 + status(PENDING/CONFIRMED/SHIPPED/DELIVERED) | 리뷰 없음, `GoodsOrderItemRepository`는 `findByOrderId`만(교차조회 부재) |

→ **품절 = 즉시 실패**(백오더 없음), 장바구니가 결제 시점 재고와 어긋날 수 있고, 구매 신뢰 신호(리뷰)·추천이 없습니다.

---

## 목표 (Goals)

- 품절 시 백오더로 **이탈 대신 대기 전환** 측정
- 장바구니-재고 불일치 결제 실패 **감소**
- 리뷰·평점으로 상품 신뢰 신호 확보
- 연관 추천으로 객단가 개선 측정

---

## 비목표 (Non-Goals)

- 가격·쿠폰 — 다이내믹 프라이싱 PRD 별도
- ML 개인화 추천 — 룰 기반(함께 구매)으로 시작
- 판매자 정산·입점 — 범위 밖

---

## 사용자 스토리 (User Stories)

| As a | I want to | So that |
|---|---|---|
| 일반 사용자 | 품절 상품 입고 알림·백오더 | 재입고 시 구매 |
| 일반 사용자 | 결제 직전 재고·가격 재확인 | 결제 실패 없음 |
| 구매자 | 구매한 상품에 리뷰·평점 | 다른 사용자에 도움 |
| 일반 사용자 | 함께 많이 산 상품 추천 | 필요한 것 발견 |

---

## 기능 요구사항 (Functional Requirements)

### FR-00. 선행 — 스키마·토픽 (병목)
- **결과**: V34~ 신규 테이블(`backorders`, `reviews`, `product_recommendations`) audit 6컬럼 + 인덱스. `goods.stock.restocked.v1` 토픽·스키마 정의(백오더 알림 producer/consumer 선행).

### FR-01. 재고 점유 모델 정비 + 백오더 — 검수 M1·M2·M3
- **결과**:
  - **기존 PENDING 즉시 실차감(`createPendingOrder`)을 AS-IS로 인정.** "예약 레이어 신설"이 아니라 **현 차감 시점·보상 흐름 정비**로 범위 재정의 — 별도 예약 테이블로 이중차감하지 않음.
  - 보상은 현행 동기 분기(`processPaymentResult` → `cancelPendingOrder` → `stock.restore`)를 기준으로 함.
  - **결제 모델 명시**: 현재 주문 생성이 동기 결정(생성 호출 종료 시 확정/취소)이므로 "결제 완료 전 TTL 만료" 시나리오는 비동기 prepare/confirm 모델 도입 시에만 성립 — 본 PRD는 **백오더(품절 시 입고 대기 주문)** 에 집중하고, 별도 예약 TTL은 오픈이슈로 분리.
  - 품절 시 `OutOfStockException` 대신 백오더 등록 옵션 + 입고 시 `goods.stock.restocked.v1` 알림.

### FR-02. 장바구니 만료 + 결제 시점 재검증
- **결과**: `Cart`/`CartItem`에 만료 정책(N일 후 비활성). 담기 시점 검증(`validateStockSufficient`)은 이미 존재 → **결제 시점 재검증 추가**로 범위 한정. 가격·재고 불일치 사용자 고지.

### FR-03. 상품 리뷰·평점 — 검수 N1
- **결과**: `Review` 엔티티(product_id, user_id, 평점 1~5, 본문, order_id). **구매자 검증** — `GoodsOrderItemRepository`에 user+product+status 교차조회 메서드 신설(현재 `findByOrderId`만). 작성 자격 상태(CONFIRMED/SHIPPED/DELIVERED 중 어디부터)는 오픈이슈. unique 제약 키(order_id별 vs product+user) 결정. 평균 평점·개수 집계.

### FR-04. 연관 추천 (함께 구매)
- **결과**: 주문 이력 기반 "함께 산 상품" 룰 집계(배치 + Redis 캐시, 실시간 산정 금지). 상품 상세·장바구니 노출.

---

## 비기능 요구사항 (Non-Functional Requirements)

- **성능**: 추천은 배치 집계 + 캐시. 결제 시점 재검증은 기존 검증 재사용.
- **동시성**: 재고 차감/복원은 기존 `Stock` `@Version` 낙관락 유지 — 중복 차감·이중 복원 0. 백오더 입고 배분 시 여러 백오더 vs 입고 수량 경합 처리(오픈이슈).
- **정합성**: 리뷰는 결정된 unique 키로 1회. 백오더 상태 전이 일관.
- **운영**: 백오더 전환율·리뷰 작성율 메트릭.

---

## 제약 조건 (Constraints)

- 기존 `Stock`(`@Version`)·`createPendingOrder` 차감·`cancelPendingOrder` 동기 보상과 충돌 없이 정비.
- Review·Backorder는 독립 Entity(`@ManyToMany` 금지). audit 6컬럼·soft delete.
- 추천은 룰 기반(외부 ML 없음). Hexagonal + Rich Domain.
- FR-01은 `GoodsDomainService.kt` 단일 파일 수정 → 타 FR과 **별도 wave**(단일 writer 충돌 방지).

---

## 영향 범위 (Scope)

| 레포 | 변경 유형 | 설명 |
|---|---|---|
| backend | 신규 | `domain/goods/` Backorder·Review·추천 집계 |
| backend | 수정 | 재고 점유 정비(`GoodsDomainService`), Cart 만료·결제시점 재검증, GoodsOrderItem 교차조회 |
| mobile / web(B2C) | 신규/수정 | 백오더·입고 알림, 리뷰 작성·표시, 추천 노출 |
| Kafka | 신설 | `goods.stock.restocked.v1` |

데이터 모델: 신규 `backorders`, `reviews`, `product_recommendations`. `carts`에 만료 컬럼.

### 확인된 누락 선행 티켓 (검수 도출)
| 제목 | 이유 |
|---|---|
| [DB] V34~ 신규 테이블 마이그레이션 | V33까지 점유, audit 6컬럼 선행 |
| [INFRA] `goods.stock.restocked.v1` 토픽·스키마 | producer/consumer 선행 병목 |
| [BE] GoodsOrderItem user+product+status 교차조회 | FR-03 구매자 검증 (현재 findByOrderId만) |
| [BE] 재고 점유 흐름 정비 (GoodsDomainService 단독 wave) | FR-01 단일 writer 병목 |

---

## 오픈 이슈 (Open Issues)

| # | 질문 | 담당 | 기한 |
|---|------|------|------|
| 1 | 별도 예약 TTL 도입 여부 — 비동기 prepare/confirm 결제 모델 전환 필요 | 기술 리드 | FR-01 전 |
| 2 | 백오더 자동 결제 vs 재방문 결제 + 입고 배분 동시성 | PO/기술 리드 | FR-01 전 |
| 3 | 장바구니 만료 기간 + 가격 변동 시 처리 | PO | FR-02 전 |
| 4 | 리뷰 작성 자격 상태(DELIVERED부터?) + unique 키 | PO | FR-03 전 |
| 5 | 추천 집계 주기·최소 동시구매 임계 | 기술 리드 | FR-04 전 |

---

## 마일스톤

| 단계 | 내용 | 목표일 |
|------|------|--------|
| v0.0 (선행) | FR-00 — 스키마·토픽 | TBD |
| v1.0 | FR-01 + FR-02 — 재고 정비·백오더·장바구니 만료 | TBD |
| v1.1 | FR-03 — 리뷰·평점 | TBD |
| v1.2 | FR-04 — 연관 추천 | TBD |

---

## 검수 이력 (prd-reviewer)

| 일자 | 판정 | Must Fix | 반영 |
|---|---|---|---|
| 2026-05-31 | NEEDS_REVISION | M1 보상이 consumer 아닌 동기 분기 / M2 PENDING 시 이미 실차감(예약 추가 시 이중차감) / M3 동기 결제라 TTL 만료 시나리오 진입점 불명 | 3건 전부 반영 — AS-IS 정정, FR-01을 점유 모델 정비+백오더로 재정의, 예약 TTL은 오픈이슈 분리, 선행 티켓 |
| 2026-05-31 | **APPROVED** (재검수) | M1~M3 전부 해소 확인 (코드 기준) | 구현 착수 가능 |

## Document History

| 날짜 | 변경 내용 | 작성자 |
|---|---|---|
| 2026-05-31 | /prd 초안 → prd-reviewer Must Fix 3건 반영 | biuea3866 |

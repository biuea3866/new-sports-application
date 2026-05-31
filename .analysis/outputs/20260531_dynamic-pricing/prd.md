# 다이내믹 프라이싱 & 프로모션 엔진 PRD

> 작성일: 2026-05-31 | 작성자: biuea3866@gmail.com
> 소스: B1 비즈니스 확장 — 서버 가격 산정 + 쿠폰/변동가로 B2C 매출 증대 + 가격 조작 취약점 정상화
> 관련 PRD: [구독/멤버십](../20260531_subscription-membership/prd.md)
> prd-reviewer 검수: 2026-05-31 NEEDS_REVISION → Must Fix 5건 반영 → **재검수 APPROVED** (하단 검수 이력)

---

## 진행 현황 (Status Ledger)

> 최종 갱신: 2026-05-31 | 전체: 0/6 배포완료 (0%)

| FR | 기능 | 상태 | 구현(PR) | 배포 | 비고 |
|----|------|------|---------|------|------|
| FR-01 | Slot 가격 필드 + Booking amount 서버 산정 전환 | ⬜ 대기 | — | — | 가격 조작 취약점 정상화 포함 |
| FR-02 | 가격 정책 엔진 (PricingRule) | ⬜ 대기 | — | — | FR-01 선행 |
| FR-03 | 쿠폰/할인코드 도메인 | ⬜ 대기 | — | — | 독립 가능 |
| FR-04 | 결제 시점 가격 산정 (Quote) | ⬜ 대기 | — | — | FR-02·03 선행 |
| FR-05 | 프로모션 캠페인 (기간·대상) | ⬜ 대기 | — | — | FR-03 선행 |
| FR-06 | 운영자 가격/쿠폰 관리 UI + MCP tool | ⬜ 대기 | — | — | FR-02·03 선행 |

### 상태 정의 (증거 기반 — `rules/COMPLETION-RULE.md` 연계)

| 상태 | 진입 조건 (증거) |
|---|---|
| ⬜ 대기 | 티켓만 존재 |
| 🟡 진행중 | 브랜치 존재 / 커밋 시작 |
| 🔵 구현완료 | dev 머지 PR + COMPLETION-RULE §1~4 충족 (테스트 통과 아티팩트) |
| ✅ 배포완료 | release 태그 / 배포 로그 첨부 |

---

## 배경 (Background)

가격 체계가 정적이고, **Booking은 가격 조작 취약점을 갖고 있습니다.** 검수에서 AS-IS를 코드 기준으로 정정했습니다.

| 도메인 | 실제 코드 상태 (검수 정정) | 한계 |
|---|---|---|
| `Slot` (시설 예약) | `Slot.kt`에 **price 필드 없음** (사실). 단 **Booking은 이미 Payment 연동됨** (`CreateBookingUseCase.kt:18-27`) | 가격 출처가 서버에 없음 |
| `Booking.amount` | **클라이언트 요청 본문에서 그대로 전달** (`CreateBookingCommand.kt:10`, `CreateBookingRequest.kt:10`) → 서버 검증 0 | **가격 조작 취약점** |
| `Seat` (티켓) | `Seat.price` 존재 + `TicketingDomainService.calculateAmount`로 **서버 산정** (`:134-140`) | 변동가 불가 (대조군: 서버산정 정상) |
| `Product` (굿즈) | `Product.price` + `order.totalAmount` **서버 산정** (`GoodsDomainService.kt:66`) | 할인·세일 불가 (대조군) |
| `Payment` | `amount` 그대로 청구, `idempotency_key` UNIQUE(100자), 멱등키는 **UUID 기반 비결정**(`CreateBookingUseCase.kt:32` `booking:{id}:{UUID}`) | 쿠폰·프로모션 단계 없음 |

→ **두 가지 문제**: (1) Booking은 amount가 클라이언트 입력이라 **가격을 조작당할 수 있음** — 정상화 필요. (2) Slot 변동가·쿠폰·프로모션 수단 전무. 본 PRD는 결제 직전에 **서버 가격 산정(Quote) 레이어**를 삽입해 두 문제를 동시에 해결합니다.

---

## 목표 (Goals)

- **Booking amount를 서버 산정으로 전환** — 가격 조작 취약점 0건
- 시설 예약 시간대·요일·잔여 기반 변동가
- 쿠폰 사용 전환율 측정 시작
- Quote 가격 결정성 — 같은 정책·시각·잔여 = 동일 금액

---

## 비목표 (Non-Goals)

- AI/ML 수요예측 변동가 — 룰 기반으로 시작
- 결제 엔진(멱등·낙관락·Saga·PG webhook) 변경 — 가격 산정 레이어만 선행 삽입
- B2C UI 대규모 개편 — 가격 표시·쿠폰 입력 필드 추가 수준
- 다중 통화 — KRW 단일

---

## 사용자 스토리 (User Stories)

| As a | I want to | So that |
|---|---|---|
| 일반 사용자 | 비피크 시간대 할인가로 예약 | 저렴하게 이용 |
| 일반 사용자 | 쿠폰코드 입력해 할인 | 혜택 |
| 일반 사용자 | 결제 전 최종 금액·할인 내역 확인 | 투명한 가격 |
| 보안/플랫폼 | 예약 금액을 서버가 확정 | 가격 조작 차단 |
| 시설 운영자 | 주말·저녁 가격을 높게 설정 | 피크 수익 |
| 운영자 | 비수기 쿠폰 발행 / MCP로 빠른 캠페인 | 수요 유도 |

---

## 기능 요구사항 (Functional Requirements)

### FR-01. Slot 가격 필드 + Booking amount 서버 산정 전환 — 검수 M1·M2·M5
- **결과**: `Slot`에 `basePrice: BigDecimal` 추가. **Booking 생성 시 amount를 클라이언트 입력 대신 서버가 Slot.basePrice(+Quote) 기준으로 산정** — `CreateBookingCommand.amount` 제거 또는 무시. 이는 기존 가격 조작 취약점 정상화. 0원(무료 슬롯/100% 할인) 경로: `Booking.confirm(paymentId: Long)`이 non-null을 요구하므로(`Booking.kt:58`), **0원 전용 confirm 경로**(결제 우회 + paymentId nullable 또는 0원 Payment 레코드) 설계. 마이그레이션: 기존 slot `basePrice` 백필 + 기존 Booking API 하위호환(amount 무시 전환) 정책.

### FR-02. 가격 정책 엔진 (PricingRule)
- **결과**: `PricingRule`(대상 범위, 조건: 요일·시간대·잔여율, 조정: 정률%/정액, 우선순위, 유효기간). 적용 순서: basePrice → rule 우선순위순 → 조정가. Seat 변동가: 잔여석 비율 기반. **결정적** — 같은 (대상, 시각, 잔여) = 같은 결과.

### FR-03. 쿠폰/할인코드 도메인
- **결과**: `Coupon`(code unique, 할인, 최소주문액, 사용한도 전체/1인, 유효기간, 대상). `CouponRedemption` 매핑 엔티티(**@ManyToMany 금지**). 발급: 공개/타겟. 검증: 만료·한도·최소금액·대상. **한도 동시성은 NFR 참조**.

### FR-04. 결제 시점 가격 산정 (Quote) — 검수 M2·M4
- **결과**: 결제 전 `PriceQuote` 산정 — 정가 → PricingRule → 쿠폰 차감 → 최종 청구액(라인 아이템 포함). 결제 시 Quote 최종액으로 `Payment.amount` 확정(서버 산정). **멱등 2종 분리 명시**:
  - **Quote 멱등 = 가격 결정성**: 같은 정책·시각·잔여 → 같은 금액. "같은 시점"은 Quote 유효시간(오픈이슈)으로 고정.
  - **Payment 멱등 = 중복 결제 방지**: 기존 `idempotency_key`(UUID 기반 비결정 키, `CreateBookingUseCase.kt:32`)와 별개 개념. Quote는 amount를 정하고, Payment 멱등은 중복 호출을 막음 — 두 키를 혼용하지 않음.

### FR-05. 프로모션 캠페인
- **결과**: `PromotionCampaign`(기간, 대상, 연결 쿠폰/룰, 활성). 시작/종료 스케줄 자동. 캠페인별 사용·매출 → 인사이트 위젯.

### FR-06. 운영자 가격/쿠폰 관리 UI + MCP tool
- **결과**: 포털 가격 정책·쿠폰·캠페인 CRUD. MCP write tool 신규: `createPricingRule`·`issueCoupon`·`startCampaign`(confirm flow 재사용). AI 어시스턴트(C2) 연계.

---

## 비기능 요구사항 (Non-Functional Requirements) — 검수 M3 신설

- **성능**: 가격 산정(Quote)은 결제 동기 경로 — **P95 100ms 이내**. PricingRule 매칭(요일·시간대·잔여율)과 Coupon 검증은 조회를 동반하므로 **PricingRule·Coupon 캐싱**(Redis, 변경 시 무효화). 잔여율 계산(`bookingRepository.countBySlotIdAndStatusIn` / Seat 잔여)은 쿼리 수 가드.
- **동시성 (쿠폰 한도)**: 동시 요청 시 1인/전체 한도 초과 차단 — **`coupon_redemptions`에 `UNIQUE(coupon_id, user_id)`(1인 한도) + 전체 한도는 낙관락(`@Version`) 또는 원자적 카운터**. 기존 패턴 참조: 좌석은 Redis `DistributedLock`, 결제는 `idempotency_key UNIQUE`.
- **정합성**: 쿠폰 redemption은 결제 성공과 원자적(보상 트랜잭션) — 결제 실패 시 redemption 롤백. Quote의 amount는 항상 서버 산정 — 클라이언트 전달 금액 신뢰 금지.
- **운영**: 캠페인별 사용·매출 메트릭. 비정상 할인율(설정 오류) 가드.

---

## 제약 조건 (Constraints)

- 기존 결제 엔진(멱등·낙관락·Saga·webhook) 변경 0 — 가격 산정 레이어만 선행 삽입.
- Booking amount 서버 전환 시 **기존 API 하위호환** 정책 필요(클라이언트 amount 무시).
- 0원 결제는 `Booking.confirm(paymentId non-null)` 제약 해소 경로 필요.
- KRW 단일. Hexagonal + Rich Domain, audit 6컬럼·soft delete, `@ManyToMany` 금지.

---

## 영향 범위 (Scope)

| 레포 | 변경 유형 | 설명 |
|---|---|---|
| backend | 신규/수정 | `domain/pricing/`, `domain/coupon/`, Slot·Booking 변경(amount 서버 산정), Payment 산정 연동, MCP tool |
| mobile / web(B2C) | 수정 | 가격·할인 내역·쿠폰 입력, Quote 조회(WAS 경유) |
| web(B2B) | 신규 | 가격·쿠폰·캠페인 관리 |
| Kafka | 신설 검토 | `coupon.redeemed.v1` (consumer 신설/기존확장 오픈이슈) |

데이터 모델: `slots`에 `base_price`. 신규 `pricing_rules`, `coupons`, `coupon_redemptions`, `promotion_campaigns`, `price_quotes`(영속화 — 감사·분쟁 대응, N1).

### 확인된 누락 선행 티켓 (검수 도출)

| 제목 | 이유 |
|---|---|
| Booking amount 서버 산정 전환 (가격 조작 취약점 정상화) | M1·M2 — 현재 클라이언트 입력 |
| 0원 결제 시 Booking confirm 경로 (paymentId non-null 해소) | M5 |

---

## 오픈 이슈 (Open Issues)

| # | 질문 | 담당 | 기한 |
|---|------|------|------|
| 1 | Slot 유료화 — 무료/유료 혼재 정책 | PO | FR-01 전 |
| 2 | 티켓 변동가 — 구매 후 변동 형평성 | PO/Legal | FR-02 전 |
| 3 | 쿠폰 + 프로모션 + 구독할인 스택 허용 | PO | FR-04 전 |
| 4 | Quote 유효시간 (가격 잠금 N분) | 기술 리드 | FR-04 전 |
| 5 | 쿠폰 한도 동시성 제어 방식 (unique/락/낙관락) | 기술 리드 | FR-03 전 |
| 6 | 가격 산정 동기 경로 성능·캐싱 | 기술 리드 | FR-02 전 |
| 7 | 0원 Booking confirm 경로 (paymentId 필수) | 기술 리드 | FR-01 전 |
| 8 | Booking amount 서버 전환 시 기존 API 하위호환 | 기술 리드/FE | FR-01 전 |
| 9 | Quote 멱등 vs Payment 멱등 정의 분리 확정 | 기술 리드 | FR-04 전 |

---

## 마일스톤

| 단계 | 내용 | 목표일 |
|------|------|--------|
| v1.0 | FR-01(amount 서버 산정 — **취약점 정상화 포함**) + FR-03 + FR-04 — 쿠폰/할인 + 서버 가격 확정 | TBD |
| v1.1 | FR-02 — Slot 변동가 정책 (UX 변경 동반) | TBD |
| v1.2 | FR-05 + FR-06 — 캠페인·운영 도구 | TBD |

> 검수 지적(M2): 쿠폰만 v1.0에 얹고 Booking amount 서버 산정을 미루면 가격 조작 취약점이 v1.0에 잔존. 따라서 **FR-01의 amount 서버 전환을 v1.0에 포함**.

---

## 검수 이력 (prd-reviewer)

| 일자 | 판정 | Must Fix | 반영 |
|---|---|---|---|
| 2026-05-31 | NEEDS_REVISION | M1 Booking 이미 결제연동(무료예약 전제 오류) / M2 amount 클라이언트 입력 취약점 미식별 / M3 NFR 전무 / M4 Quote↔Payment 멱등 모순 / M5 0원 경로 부재 | 5건 전부 반영 — AS-IS 정정, FR-01에 amount 서버 전환, NFR 신설, 멱등 분리, 0원 경로, 오픈이슈 5건 추가 |
| 2026-05-31 | **APPROVED** (재검수) | M1~M5 전부 해소 확인 (코드 인용 라인 전수 일치) | 구현 착수 가능 |

## Document History

| 날짜 | 변경 내용 | 작성자 |
|---|---|---|
| 2026-05-31 | 초안 → /prd 표준 → prd-reviewer Must Fix 5건 반영 | biuea3866 |

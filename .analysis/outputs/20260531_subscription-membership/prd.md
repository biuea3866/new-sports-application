# 구독 & 멤버십 (Subscription & Membership) PRD

> 작성일: 2026-05-31
> 작성자: biuea3866@gmail.com
> 소스: B2 비즈니스 확장 아이디어 — 정기결제로 LTV 상승 (시설 무제한·프리미엄·굿즈 할인)
> 관련 PRD: [다이내믹 프라이싱](../20260531_dynamic-pricing/prd.md), [B2B 멀티테넌시](../20260531_b2b-multitenancy/prd.md)

---

## 진행 현황 (Status Ledger)

> 최종 갱신: 2026-05-31 | 전체: 0/6 배포완료 (0%)

| FR | 기능 | 상태 | 구현(PR) | 배포 | 비고 |
|----|------|------|---------|------|------|
| FR-01 | 구독 플랜 + 혜택 도메인 | ⬜ 대기 | — | — | 전 FR 선행 |
| FR-02 | PG 빌링키 정기결제 연동 | ⬜ 대기 | — | — | FR-01 선행, Payment 확장 |
| FR-03 | 구독 라이프사이클 (가입/갱신/해지) | ⬜ 대기 | — | — | FR-01·02 선행 |
| FR-04 | 정기결제 스케줄러 + 실패 재시도 | ⬜ 대기 | — | — | FR-02 선행 |
| FR-05 | 혜택 적용 (예약/티켓/굿즈 권한·할인) | ⬜ 대기 | — | — | FR-01 선행 |
| FR-06 | 구독 관리 UI (B2C 마이페이지) | ⬜ 대기 | — | — | FR-03 선행 |

### 상태 정의 (증거 기반 — `rules/COMPLETION-RULE.md` 연계)

| 상태 | 진입 조건 (증거) |
|---|---|
| ⬜ 대기 | 티켓만 존재 |
| 🟡 진행중 | 브랜치 존재 / 커밋 시작 |
| 🔵 구현완료 | dev 머지 PR + COMPLETION-RULE §1~4 충족 (테스트 통과 아티팩트) |
| ✅ 배포완료 | release 태그 / 배포 로그 첨부 |

---

## 배경 (Background)

현재 결제는 **단건(one-time)만** 지원합니다.

| 자산 | 현 구조 | 한계 |
|---|---|---|
| `Payment` | `idempotencyKey`, `orderType`, `orderId`, `amount`, `version`(낙관락), PG webhook | **정기결제(recurring)·빌링키 필드 없음** |
| `OrderType` | BOOKING / TICKET / GOODS | SUBSCRIPTION 없음 |
| PG 연동 | 웹뷰 prepare + webhook (단건) | 빌링키 발급·정기 청구 미지원 |

→ 결과: **반복 매출(MRR) 모델이 불가능**합니다. 모든 매출이 1회성 거래입니다.

### 비즈니스 기회

| 모델 | 설명 |
|---|---|
| 시설 무제한 멤버십 | 월정액 → 예약 무제한/할인 |
| 프리미엄 티켓 | 좌석 우선 선택권·선예매 |
| 굿즈 정기 할인 | 멤버 전용가·무료배송 |
| 번들 | 시설+티켓+굿즈 통합 멤버십 |

본 PRD는 기존 결제 엔진에 **정기결제(빌링키 + 스케줄)** 를 더하고, 구독 상태에 따라 도메인 혜택을 적용합니다.

---

## 목표 (Goals)

- 구독 플랜과 혜택을 정의하는 도메인을 도입한다 (FR-01)
- PG 빌링키 기반 정기결제를 연동한다 (FR-02)
- 구독 가입·갱신·해지 라이프사이클을 관리한다 (FR-03)
- 정기 청구 스케줄러 + 결제 실패 재시도/유예를 구현한다 (FR-04)
- 구독 상태에 따라 예약·티켓·굿즈 혜택을 적용한다 (FR-05)
- 사용자가 마이페이지에서 구독을 관리한다 (FR-06)

### 측정 가능 목표 (KPI)

| 지표 | 현재 | 목표 |
|---|---|---|
| 반복 매출(MRR) | 0 (단건만) | 측정 시작 |
| 정기결제 멱등성 | N/A | 같은 주기 중복 청구 0건 |
| 결제 실패 복구율 | N/A | 재시도로 N% 회수 |

---

## 비목표 (Non-Goals)

- **결제 엔진 재작성** — 기존 Payment(멱등·낙관락·Saga) 재사용, 정기결제 필드·스케줄만 추가
- **실 PG 빌링 깊은 연동** — mock PG에 빌링키 시뮬레이션 우선, 실 PG는 어댑터 교체
- **B2B 조직 구독** — 멀티테넌시 PRD(C1 FR-05)와 엔진 공유하되, 본 PRD는 **B2C 개인 구독**에 집중
- **복잡한 비례배분(proration)** — 플랜 변경 시 단순 차기 주기 적용으로 시작

---

## 사용자 스토리 (User Stories)

| As a | I want to | So that |
|---|---|---|
| 일반 사용자 | 월정액 멤버십 가입 | 시설 무제한·할인 |
| 일반 사용자 | 매월 자동 결제 | 매번 결제 안 함 |
| 일반 사용자 | 언제든 해지 | 부담 없이 시작 |
| 멤버 | 프리미엄 좌석 우선 선택 | 좋은 자리 확보 |
| 멤버 | 굿즈 멤버가 적용 | 저렴하게 구매 |
| 사용자 | 결제 실패 시 알림받고 카드 갱신 | 의도치 않은 해지 방지 |
| 운영자 | 구독자 현황·이탈 추적 | 리텐션 관리 |

---

## 기능 요구사항 (Functional Requirements)

### FR-01. 구독 플랜 + 혜택 도메인

**결과**:
- `SubscriptionPlan` 엔티티: name, price, billing_cycle(MONTHLY/YEARLY), 혜택 정의(benefits_json)
- `PlanBenefit`: 예약 무제한/N회, 티켓 우선권, 굿즈 할인율 등 혜택 종류
- `Subscription` 엔티티: user_id, plan_id, status(ACTIVE/PAST_DUE/CANCELLED/EXPIRED), current_period_start/end, billing_key 참조, audit 6
- 상태 전이는 enum `canTransitTo()` 캡슐화 (Rich Domain)

### FR-02. PG 빌링키 정기결제 연동

**결과**:
- `BillingKey` 엔티티: user_id, pg_billing_key(암호화), card_masked, status
- `RecurringPaymentGateway` (domain interface) — 빌링키 발급·정기 청구·해지
- 기존 `Payment`에 `OrderType.SUBSCRIPTION` + `subscription_id` 추가 (정기결제도 Payment 레코드로 일원화)
- 빌링키는 카드 정보 비저장 — PG 토큰만 보관 (PCI 회피)

<details>
<summary>OrderType 확장</summary>

```
현재: BOOKING / TICKET / GOODS
추가: SUBSCRIPTION
→ 정기 청구도 Payment 멱등키·낙관락·webhook 흐름 재사용
```

</details>

### FR-03. 구독 라이프사이클 (가입/갱신/해지)

**결과**:
- 가입: 플랜 선택 → 빌링키 발급 → 첫 결제 → Subscription ACTIVE
- 갱신: 주기 종료 시 자동 (FR-04 스케줄러)
- 해지: 즉시 해지 vs 주기말 해지 — 주기말 기본 (잔여 기간 혜택 유지)
- 플랜 변경: 차기 주기부터 적용

### FR-04. 정기결제 스케줄러 + 실패 재시도

**결과**:
- 스케줄러: `current_period_end` 도래 구독을 빌링키로 청구 (`@Scheduled` cron, `McpAnomalyScheduler` 패턴 참조)
- **멱등 강제**: 같은 구독 + 같은 주기는 1회만 청구 (멱등키 = subscription_id + period)
- 실패 시: PAST_DUE 전환 → 재시도(지수 백오프 N회) → 최종 실패 시 알림 + 유예 후 EXPIRED
- 결제 실패·임박 시 기존 알림(Notification) 채널로 통보 (카드 갱신 유도)

### FR-05. 혜택 적용 (예약/티켓/굿즈)

**결과**:
- 예약: ACTIVE 구독자는 무제한/할인 — Booking 생성 시 구독 혜택 조회
- 티켓: 프리미엄 멤버 좌석 선예매·우선권 (Redis 락 단계에서 멤버 우선)
- 굿즈: 결제 Quote 산정 시 멤버 할인 적용 — **다이내믹 프라이싱 PRD(B1)의 Quote 엔진과 연계**
- 혜택 적용은 구독 status 실시간 확인 (만료·연체 즉시 반영)

### FR-06. 구독 관리 UI (B2C 마이페이지)

**결과**:
- 마이페이지: 현재 플랜·다음 청구일·결제 수단·이용 내역
- 플랜 변경·해지·빌링키(카드) 교체
- 모바일·웹 공통 (기존 마이페이지 탭 확장)

---

## 데이터 모델 (신규 + 변경)

| 테이블 | 유형 | 주요 컬럼 |
|---|---|---|
| `subscription_plans` | 신규 | name, price, billing_cycle, benefits_json, active, audit 6 |
| `subscriptions` | 신규 | user_id, plan_id, status, current_period_start/end, billing_key_id, audit 6 |
| `billing_keys` | 신규 | user_id, pg_billing_key(암호화), card_masked, status, audit 6 |
| `payments` | **컬럼 추가** | `subscription_id BIGINT NULL` + OrderType SUBSCRIPTION |
| `OrderType` enum | **변경** | + SUBSCRIPTION |

> `billing_keys`는 카드 원본 비저장 — PG 토큰만. 암호화 컬럼 + 접근 audit 필수 (보안).

---

## 영향 서비스

| 서비스 | 레포 | 변경 |
|---|---|---|
| backend | `/backend` | 신규 `domain/subscription/`, `RecurringPaymentGateway`, 정기 스케줄러, Payment·OrderType 확장, 혜택 조회 |
| mock-pg | `/mock-pg` | 빌링키 발급·정기청구 시뮬레이션 |
| B2C 모바일/웹 | `/mobile`, `/web` | 구독 가입·관리 화면, 혜택 표시 |
| B2B 포털 | `/web` | 구독자 현황·이탈 대시보드 (선택) |

### Kafka 변경

| 토픽 | 유형 | Producer | Consumer |
|---|---|---|---|
| `subscription.activated.v1` | 신설 | Subscription | Notification(환영), 인사이트 |
| `subscription.payment.failed.v1` | 신설 | 정기 스케줄러 | Notification(카드갱신 유도) |
| `subscription.cancelled.v1` | 신설 | Subscription | 혜택 해제, 인사이트(이탈) |

---

## 오픈 이슈 / 결정 필요

| # | 이슈 | 영향 FR | 결정권자 |
|---|---|---|---|
| 1 | 실 PG 선택 + 빌링키 지원 여부 (Toss/이니시스 등) | FR-02 | 기술 리드/사업 |
| 2 | 해지 정책 — 즉시 환불 vs 주기말 (Legal) | FR-03 | Legal/PO |
| 3 | 결제 실패 유예 기간·재시도 횟수 | FR-04 | PO |
| 4 | 혜택 충돌 — 구독 할인 + 쿠폰(B1) 스택 허용? | FR-05 | PO |
| 5 | B2B 테넌트 구독(C1 FR-05)과 엔진 공유 범위 | FR-01·02 | 기술 리드 |

---

## 출시 범위 (제안)

- **v1.0**: FR-01 + FR-02 + FR-03 + FR-06 — 단일 플랜 가입·정기결제·해지·관리 UI. **혜택은 단순(굿즈 할인 1종)부터**.
- **v1.1**: FR-04 강화 (재시도·유예 정교화) + FR-05 확장 (예약 무제한·티켓 우선권).

> FR-02(빌링키)가 기술 핵심 리스크 — 실 PG 정기결제 스펙에 의존. mock-pg로 플로우를 먼저 완성하고 실 PG는 어댑터 교체로 분리합니다. 정기결제 **멱등(중복청구 0)** 이 신뢰의 핵심.

---

## Document History

| 날짜 | 변경 내용 | 작성자 |
|---|---|---|
| 2026-05-31 | 초안 작성 (B2 아이디어 → PRD) | biuea3866 |

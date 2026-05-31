# 구독 & 멤버십 (Subscription & Membership) PRD

> 작성일: 2026-05-31 | 작성자: biuea3866@gmail.com
> 소스: B2 비즈니스 확장 — 정기결제로 LTV 상승 (시설 무제한·프리미엄·굿즈 할인)
> 관련 PRD: [다이내믹 프라이싱](../20260531_dynamic-pricing/prd.md)
> prd-reviewer 검수: 2026-05-31 NEEDS_REVISION → Must Fix 4건 반영 → **재검수 APPROVED** (하단 검수 이력)

---

## 진행 현황 (Status Ledger)

> 최종 갱신: 2026-05-31 | 전체: 0/8 배포완료 (0%)

| FR | 기능 | 상태 | 구현(PR) | 배포 | 비고 |
|----|------|------|---------|------|------|
| FR-00 | 빌링키 암호화 컨버터 + 키 관리 (선행) | ⬜ 대기 | — | — | M3 — 암호화 AttributeConverter 0건 |
| FR-01 | 구독 플랜 + 혜택 + 상태 전이표 도메인 | ⬜ 대기 | — | — | M4 — 전이표 명시 |
| FR-02 | PG 빌링키 정기결제 연동 | ⬜ 대기 | — | — | FR-00·01 선행, OrderType TICKETING |
| FR-03 | 구독 라이프사이클 (가입/갱신/해지) | ⬜ 대기 | — | — | FR-01·02 선행 |
| FR-04 | 정기결제 스케줄러 + 멱등 + 실패 재시도 | ⬜ 대기 | — | — | M2 — 멱등키·재시도 충돌 해소 |
| FR-05 | 혜택 적용 (예약/티켓/굿즈) | ⬜ 대기 | — | — | FR-01 선행, 프라이싱 PRD 연계 |
| FR-06 | 구독 관리 UI (B2C 마이페이지) | ⬜ 대기 | — | — | FR-03 선행 |
| FR-07 | 정기청구 실패 기록 경로 (Payment FAILED) | ⬜ 대기 | — | — | M4 — webhook FAILED 미처리 |

### 상태 정의 (증거 기반 — `rules/COMPLETION-RULE.md` 연계)

| 상태 | 진입 조건 (증거) |
|---|---|
| ⬜ 대기 | 티켓만 존재 |
| 🟡 진행중 | 브랜치 존재 / 커밋 시작 |
| 🔵 구현완료 | dev 머지 PR + COMPLETION-RULE §1~4 충족 (테스트 통과 아티팩트) |
| ✅ 배포완료 | release 태그 / 배포 로그 첨부 |

---

## 배경 (Background)

결제는 **단건(one-time)만** 지원합니다. AS-IS는 검수에서 코드 대조로 정정했습니다.

| 자산 | 실제 코드 상태 (검수 정정) | 한계 |
|---|---|---|
| `OrderType` | `OrderType.kt:3-7` **`BOOKING / TICKETING / GOODS`** (PRD 초안의 "TICKET"은 오류 — M1) | SUBSCRIPTION 없음 |
| `Payment` | `idempotency_key` UNIQUE(100자), 멱등키 UUID 기반 비결정(`CreateBookingUseCase.kt:32`) | 정기결제·빌링키 필드 없음 |
| PG webhook | `PaymentDomainService.kt:93-108` **APPROVED/CANCELED만 처리, FAILED 경로 없음** | 정기청구 실패 미처리 (M4) |
| 빌링키 암호화 | **암호화 `AttributeConverter` 0건** (`ZonedDateTimeAttributeConverter`만 존재) | 빌링키 저장 인프라 부재 (M3) |
| 정기 스케줄러 | `McpAnomalyScheduler.kt:20` 단순 cron, **분산 락 없음** | 다중 인스턴스 중복 청구 위험 (N2) |

→ **반복 매출(MRR) 모델이 불가능**합니다. 본 PRD는 기존 결제 엔진에 정기결제(빌링키+스케줄)를 더하되, 검수에서 드러난 멱등·보안·상태 전이 공백을 함께 메웁니다.

---

## 목표 (Goals)

- 반복 매출(MRR) 측정 시작 (현재 0)
- 정기결제 멱등 — 같은 주기 **중복 청구 0건** (재시도와 양립)
- 빌링키 카드 원본 비저장 (PG 토큰만 — PCI SAQ-A 유지)
- 결제 실패 재시도로 이탈 회수율 측정

---

## 비목표 (Non-Goals)

- 결제 엔진 재작성 — 기존 Payment 재사용, 정기결제 필드·스케줄만 추가
- 실 PG 빌링 깊은 연동 — mock-pg 시뮬레이션 우선
- B2B 조직 구독 — 본 PRD는 B2C 개인 구독에 집중
- 복잡한 비례배분(proration) — 차기 주기 적용으로 시작

---

## 사용자 스토리 (User Stories)

| As a | I want to | So that |
|---|---|---|
| 일반 사용자 | 월정액 멤버십 가입 + 자동 결제 | 시설 무제한·할인, 매번 결제 안 함 |
| 일반 사용자 | 언제든 해지 | 부담 없이 시작 |
| 멤버 | 프리미엄 좌석 우선 / 굿즈 멤버가 | 좋은 자리·저렴 구매 |
| 사용자 | 결제 실패 시 알림받고 카드 갱신 | 의도치 않은 해지 방지 |
| 운영자 | 구독자 현황·이탈 추적 | 리텐션 관리 |

---

## 기능 요구사항 (Functional Requirements)

### FR-00. 빌링키 암호화 컨버터 + 키 관리 (선행) — 검수 M3
- **결과**: 현재 암호화 `AttributeConverter`가 0건이므로, 빌링키 저장 전 **암복호화 컨버터(AES) + 키 관리(KMS/환경변수) 인프라 신설**. `pg_billing_key` 암호화 컬럼. 빌링키 **조회 접근 감사** 메커니즘(기존 `JpaAuditingBase`는 생성/수정자만 기록 → 조회 로그 별도).

### FR-01. 구독 플랜 + 혜택 + 상태 전이표 도메인 — 검수 M4
- **결과**: `SubscriptionPlan`(name, price, billing_cycle, benefits_json). `Subscription`(user_id, plan_id, status, current_period_start/end, billing_key 참조). **상태 전이표 명시**(`canTransitTo` 캡슐화):
  - `PENDING`(첫 결제 진행) → `ACTIVE` / `EXPIRED`(첫 결제 실패)
  - `ACTIVE` → `PAST_DUE`(청구 실패) / `CANCEL_SCHEDULED`(주기말 해지 예약, 혜택 유지) / `CANCELLED`(즉시)
  - `PAST_DUE` → `ACTIVE`(재시도 성공/카드 갱신 **회복**) / `EXPIRED`(유예 만료)
  - `CANCEL_SCHEDULED` → `EXPIRED`(주기말 도달)
  - 허용/금지 전이 전수 표를 도메인에 캡슐화.

### FR-02. PG 빌링키 정기결제 연동
- **결과**: `BillingKey`(user_id, pg_billing_key 암호화[FR-00], card_masked, status). `RecurringPaymentGateway`(domain interface). 기존 `Payment`에 **`OrderType.SUBSCRIPTION`**(기존 enum: BOOKING/TICKETING/GOODS) + `subscription_id` 추가. **PCI 데이터 플로우 명시**: 카드 PAN은 PG 위젯/웹뷰가 직접 수집, **우리 서버 미경유**(SAQ-A 유지). 빌링키 토큰만 수신.

### FR-03. 구독 라이프사이클 (가입/갱신/해지)
- **결과**: 가입(플랜 → 빌링키 발급 → 첫 결제 PENDING → ACTIVE). 갱신(FR-04 자동). 해지(주기말 기본 = CANCEL_SCHEDULED, 잔여 혜택 유지 / 즉시 = CANCELLED). 플랜 변경(차기 주기).

### FR-04. 정기결제 스케줄러 + 멱등 + 실패 재시도 — 검수 M2·N2
- **결과**: `current_period_end` 도래 구독을 빌링키로 청구. **멱등키 설계**:
  - 성공 청구 멱등 = `sub:{subscription_id}:{period}` (결정적, **주기당 성공 Payment 1건** 보장)
  - 재시도는 차원 분리 = `sub:{subscription_id}:{period}:{attempt}` — 같은 키로 FAILED Payment를 재반환해 재시도가 막히는 문제 해소(`PaymentDomainService.kt:31` `findByIdempotencyKey` 동작 회피)
  - 100자 길이 적합성 검증
  - **스케줄러 분산 락**(ShedLock 등) 또는 단일 인스턴스 보장 — 다중 인스턴스 중복 청구 차단(`McpAnomalyScheduler`는 락 없음)
- 실패 → PAST_DUE → 지수 백오프 N회 → 유예 후 EXPIRED. 실패·임박 시 Notification 통보.

### FR-05. 혜택 적용 (예약/티켓/굿즈)
- **결과**: 예약 무제한/할인, 티켓 멤버 선예매(Redis 락 단계 우선), 굿즈 멤버 할인. **혜택 적용은 pull(status 실시간 조회) 방식으로 통일** — 이벤트 push "혜택 해제" Consumer는 불필요(N5). 굿즈 할인은 프라이싱 PRD Quote 엔진 연계, **단 v1.0은 Quote 엔진 부재 시 자체 할인 계산 대안 명시**(N1).

### FR-06. 구독 관리 UI (B2C 마이페이지)
- **결과**: 현재 플랜·다음 청구일·결제 수단·이용 내역. 플랜 변경·해지·빌링키 교체. 모바일·웹 공통.

### FR-07. 정기청구 실패 기록 경로 (Payment FAILED) — 검수 M4
- **결과**: 현재 webhook은 APPROVED/CANCELED만 처리하고 FAILED 미인식. 정기청구는 **스케줄러 동기 청구**라 webhook과 진입점이 다름 — 정기청구 실패를 Payment에 기록하는 경로 신설(`PaymentStatus.READY → FAILED` 전이 활용). webhook FAILED 이벤트 타입 신설 여부 결정.

---

## 비기능 요구사항 (Non-Functional Requirements)

- **성능**: 혜택 조회는 거래 경로 — 구독 상태 캐시(만료 시각 기준 무효화). 정기 스케줄러는 배치, 건별 격리(한 청구 실패가 배치 전체를 막지 않음).
- **보안**: 빌링키 PAN 비경유(PCI SAQ-A). `pg_billing_key` 암호화(FR-00) + 조회 접근 감사. 정기 청구는 서버 스케줄러만 트리거.
- **정합성**: 정기결제 멱등(성공 1건 보장 + 재시도 차원 분리, M2). 해지·만료 시 혜택 즉시 해제(pull 방식). webhook 멱등 기존 보장 재사용.
- **운영**: 청구 성공/실패율, PAST_DUE 전환율, 이탈율 메트릭. 실패 재시도 DLQ. 스케줄러 분산 락 모니터링.

---

## 제약 조건 (Constraints)

- 기존 Payment(멱등·낙관락·Saga·webhook) 재사용 — OrderType 확장 + 스케줄러로 흡수.
- mock-pg로 빌링키 플로우 선완성, 실 PG는 `RecurringPaymentGateway` 교체.
- 빌링키 암호화·키 관리 인프라(FR-00)가 FR-02 선행.
- Hexagonal + Rich Domain, audit 6컬럼·soft delete, `@ManyToMany` 금지.

---

## 영향 범위 (Scope)

| 레포 | 변경 유형 | 설명 |
|---|---|---|
| backend | 신규 | `domain/subscription/`, `RecurringPaymentGateway`, 암호화 컨버터(FR-00), 정기 스케줄러(분산 락), 혜택 조회 |
| backend | 수정 | Payment·OrderType(SUBSCRIPTION) 확장, webhook FAILED 경로(FR-07) |
| mock-pg | 수정 | 빌링키 발급·정기청구 시뮬레이션 |
| mobile / web(B2C) | 신규/수정 | 구독 가입·관리, 혜택 표시 |
| Kafka | 신설 | `subscription.activated.v1` / `subscription.payment.failed.v1` (cancelled는 pull 방식이라 불필요 검토, N5) |

데이터 모델: 신규 `subscription_plans`/`subscriptions`/`billing_keys`. 변경 — `payments`에 `subscription_id`, `OrderType`에 SUBSCRIPTION.

### 확인된 누락 선행 티켓 (검수 도출)

| 제목 | 이유 |
|---|---|
| 빌링키 암호화 컨버터 + 키 관리 인프라 | M3 — 암호화 컨버터 0건 |
| 빌링키 조회 접근 감사 | M3 — JpaAuditingBase는 조회 미기록 |
| 스케줄러 분산 락 (중복 청구 방지) | N2 — McpAnomalyScheduler 락 없음 |
| Payment FAILED webhook/정기청구 실패 기록 경로 | M4 — 현 webhook APPROVED/CANCELED만 |

---

## 오픈 이슈 (Open Issues)

| # | 질문 | 담당 | 기한 |
|---|------|------|------|
| 1 | 실 PG 선택 + 빌링키 지원 (Toss/이니시스) | 기술 리드/사업 | FR-02 전 |
| 2 | 해지 정책 — 즉시 환불 vs 주기말 | Legal/PO | FR-03 전 |
| 3 | 결제 실패 유예 기간·재시도 횟수 | PO | FR-04 전 |
| 4 | 혜택 충돌 — 구독 할인 + 쿠폰(B1) 스택 | PO | FR-05 전 |
| 6 | 정기청구 확정 방식 — 동기 응답 vs webhook | 기술 리드 | FR-04 전 |
| 7 | 멱등키 포맷·재시도 키 차원 확정 (100자) | 기술 리드 | FR-04 전 |
| 8 | 빌링키 암호화 키 관리 주체·로테이션 | 보안 | FR-00 전 |
| 9 | 스케줄러 분산 락 방식 (ShedLock 등) | 기술 리드 | FR-04 전 |
| 10 | 구독 플랜 가격 통화 (단일 vs 다통화) | PO | FR-01 전 |

---

## 마일스톤

| 단계 | 내용 | 목표일 |
|------|------|--------|
| v0.0 (선행) | FR-00 — 빌링키 암호화·키 관리 | TBD |
| v1.0 | FR-01 + FR-02 + FR-03 + FR-06 + FR-07 — 가입·정기결제·해지·관리 UI·실패 기록 (혜택 단순 1종) | TBD |
| v1.1 | FR-04 강화(재시도·분산락) + FR-05 확장(예약 무제한·티켓 우선권) | TBD |

> FR-02(빌링키)·FR-04(멱등·재시도) 기술 핵심 리스크. 정기결제 멱등(중복청구 0)이 신뢰 핵심. N1(Quote 엔진 부재) 때문에 v1.0 굿즈 할인 산정 방식 조기 결정.

---

## 검수 이력 (prd-reviewer)

| 일자 | 판정 | Must Fix | 반영 |
|---|---|---|---|
| 2026-05-31 | NEEDS_REVISION | M1 OrderType TICKET→TICKETING 사실오류 / M2 멱등키·재시도 충돌 / M3 빌링키 암호화 인프라·PCI 공백 / M4 상태 전이·FAILED 경로 누락 | 4건 전부 반영 — AS-IS 정정, FR-00 암호화 선행, FR-01 상태 전이표, FR-04 멱등키 차원 분리+분산락, FR-07 실패 기록, NFR·오픈이슈 보강 |
| 2026-05-31 | **APPROVED** (재검수) | M1~M4 전부 해소 확인 (코드 기준) | 구현 착수 가능 |

## Document History

| 날짜 | 변경 내용 | 작성자 |
|---|---|---|
| 2026-05-31 | 초안 → /prd 표준 → prd-reviewer Must Fix 4건 반영 | biuea3866 |

# Booking 고도화 (대기열·취소정책·반복예약) PRD

> 작성일: 2026-05-31 | 작성자: biuea3866@gmail.com
> 소스: B 도메인 고도화 — 예약 전환율·수익 직결
> prd-reviewer 검수: 2026-05-31 NEEDS_REVISION → Must Fix 4건 반영 → **재검수 APPROVED** (하단 검수 이력)

---

## 진행 현황 (Status Ledger)

> 최종 갱신: 2026-05-31 | 전체: 0/5 배포완료 (0%)

| FR | 기능 | 상태 | 구현(PR) | 배포 | 비고 |
|----|------|------|---------|------|------|
| FR-00 | waitlists·NO_SHOW·penalty 스키마 + Waitlist enum (선행) | ⬜ 대기 | — | — | 공통 DDL 병목 |
| FR-01 | 대기열(Waitlist) + 취소 시 자동 승계 | ⬜ 대기 | — | — | in-process 이벤트 리스너 |
| FR-02 | 노쇼/취소 위약 정책 (+부분환불) | ⬜ 대기 | — | — | PaymentRefundGateway 재사용 |
| FR-03 | 반복 예약 (매주 같은 시간) | ⬜ 대기 | — | — | 슬롯 일괄 생성 |
| FR-04 | 대기열·노쇼 알림 연계 | ⬜ 대기 | — | — | FR-01·02 후행 |

### 상태 정의 (증거 기반 — `rules/COMPLETION-RULE.md` 연계)

| 상태 | 진입 조건 (증거) |
|---|---|
| ⬜ 대기 | 티켓만 존재 |
| 🟡 진행중 | 브랜치 존재 / 커밋 시작 |
| 🔵 구현완료 | dev 머지 PR + COMPLETION-RULE §1~4 충족 (테스트 통과 아티팩트) |
| ✅ 배포완료 | release 태그 / 배포 로그 첨부 |

---

## 배경 (Background)

Booking 도메인은 예약 생성·취소·조회와 상태 전이 가드를 갖췄습니다. AS-IS는 검수에서 코드 대조로 정정했습니다.

| 자산 (코드 확인) | 현 구조 | 한계 |
|---|---|---|
| `BookingStatus` | **PENDING / CONFIRMED / CANCELLED / EXPIRED / REFUNDED** 5개, `canTransitTo()` 캡슐화 (CANCELLED/EXPIRED/REFUNDED 종단) | NO_SHOW 없음 |
| `BookingDomainService` | `requestBooking`이 `"booking:slot:$slotId"` Redis 락(spinLock 5s/50ms/TTL 10s) | 만석 시 **대기 수단 없음** |
| `RefundPolicyViolationException` | **상태 전이 가드** — `canTransitTo(REFUNDED)` 실패 시 throw (`Booking.refund()`). 위약율 정책 아님 | 시점별 위약 정책 없음 |
| `PaymentRefundGateway.requestRefund(paymentId, amount, reason)` + `BookingDomainService.refundBooking(refundAmount)` | **부분 환불 경로 이미 존재** | 위약율 적용 환불 미연결 |
| `BookingCancelledEvent` | `AbstractDomainEvent(aggregateId=bookingId)`, **topic=null → in-process(SpringDomainEventPublisher)** | Kafka 토픽 아님 |
| `aggregateFacilityKpi` | **REFUNDED를 노쇼 proxy로 사용**(코드 주석: NO_SHOW 추가 시 대체 요망) | — |
| `Slot` | 단건 시간대 | 반복 예약 불가 |

→ 만석 시 사용자 이탈, 노쇼·취소 통제 정책 부재, 정기 이용자 매주 수동 예약. 본 PRD는 예약 전환율과 슬롯 가동률을 높입니다.

---

## 목표 (Goals)

- 만석 슬롯 대기 → 취소 시 **자동 승계**로 빈자리 최소화
- 취소·노쇼 위약 정책으로 **노쇼율 감소** 측정
- 반복 예약으로 정기 이용자 예약 단계 **N회 → 1회**

---

## 비목표 (Non-Goals)

- 신규 가격 책정 — 다이내믹 프라이싱 PRD 별도. **단 위약 환불은 기존 `PaymentRefundGateway` 부분 환불 재사용**(신규 결제 로직 아님 — FR-02 참조)
- 예약 추천(AI) — 범위 밖
- 시설 운영자 정산 — 범위 밖

---

## 사용자 스토리 (User Stories)

| As a | I want to | So that |
|---|---|---|
| 일반 사용자 | 만석 슬롯에 대기 등록 | 취소 나면 잡을 수 있음 |
| 일반 사용자 | 대기 순번이 오면 알림 받고 확정 | 빈자리 즉시 차지 |
| 시설 운영자 | 노쇼·잦은 취소에 위약 적용 | 노쇼 손실 감소 |
| 정기 이용자 | 매주 같은 시간 한 번에 예약 | 매번 예약 안 함 |

---

## 기능 요구사항 (Functional Requirements)

### FR-00. 선행 스키마 (공통 병목)
- **결과**: `waitlists` 테이블(slot_id, user_id, 순번, status, audit 6, 인덱스 `(slot_id, status, 순번)`), `bookings.status`에 NO_SHOW 추가, `booking_penalties`(또는 Booking 컬럼). `Waitlist` 상태 enum(WAITING/PROMOTED/EXPIRED) + `canTransitTo`. 후속 FR 전체의 선행 티켓.

### FR-01. 대기열(Waitlist) + 취소 시 자동 승계 — 검수 M1
- **트리거**: 만석 슬롯 대기 등록 → 해당 슬롯 예약 취소
- **승계 메커니즘**: `BookingCancelledEvent`가 **in-process 이벤트**(topic=null)이므로, 승계는 **`@TransactionalEventListener(AFTER_COMMIT)`** 로 구현(presentation layer). Kafka 토픽 신설하지 않음("신규 토픽 불필요" 정정 — in-process 리스너 사용).
- **결과**: 취소 커밋 후 다음 대기자에게 점유 기회 부여. **기존 `"booking:slot:$slotId"` Redis 락 재사용**(승계와 일반 예약이 같은 슬롯 락 경합 — 1명만 확정). 유예 시간 내 미확정 시 다음 순번 승계 + 알림. 본인 중복 대기 차단.

### FR-02. 노쇼/취소 위약 정책 (+부분환불) — 검수 M2·M3·M4
- **결과**:
  - `BookingStatus`에 NO_SHOW 추가. **전이 규칙**: CONFIRMED → NO_SHOW(종단), 위약 부분환불 시 NO_SHOW/CANCELLED → REFUNDED 허용 여부 명시. `canTransitTo` 갱신.
  - **위약율 정책은 별도 도메인 정책 객체**(`CancellationPolicy` 등) — `RefundPolicyViolationException` "확장"이 아님(그 예외는 전이 가드). 시점별 위약(24h 전 무료/이후 N%) 계산.
  - **부분 환불은 기존 `PaymentRefundGateway.requestRefund` + `refundBooking(refundAmount)` 재사용** — 위약율 적용한 refundAmount 산정. (비목표의 "결제 변경 별도"와 충돌 해소: 신규 결제 로직이 아니라 기존 환불 게이트 호출)
  - **`aggregateFacilityKpi` 노쇼율 산정을 REFUNDED proxy → 실제 NO_SHOW 집계로 전환** (코드 주석 요구 반영).
  - 누적 노쇼 페널티(카운팅 윈도우·해제 조건은 오픈이슈).

### FR-03. 반복 예약 (매주 같은 시간)
- **결과**: 반복 규칙(요일·시간·횟수)으로 다수 슬롯 일괄 예약. 일부 만석 시 부분 성공 + 실패분 대기열 등록 옵션 — **부분 성공 트랜잭션 경계 명시**(슬롯별 락 순차, 실패 시 이미 생성분 유지 vs 롤백 결정). 일괄 취소 시 `BookingCancelledEvent` N건 → FR-01 승계 N회 연쇄의 종단 일관성 시나리오 검증.

### FR-04. 대기열·노쇼 알림 연계
- **결과**: 승계 기회·노쇼 경고·반복 결과를 기존 `NotificationChannelGateway`로 통보. **FR-01·FR-02 이벤트 확정 후행**(blocked by FR-01).

---

## 비기능 요구사항 (Non-Functional Requirements)

- **성능**: 승계는 취소 AFTER_COMMIT 후 수 초 내. 대기열 조회 인덱스(`slot_id, status, 순번`).
- **동시성**: 승계 확정은 기존 `"booking:slot:$slotId"` Redis 락 + `REQUIRES_NEW`/`afterCompletion` unlock 패턴 재사용. 반복 예약도 슬롯별 락.
- **정합성**: 승계 기회 만료 시각·중복 확정 차단. 취소→승계→재취소 연쇄 종단 상태 일관. 위약 부분환불은 멱등.
- **운영**: 대기열 길이·승계 성공률·노쇼율 메트릭.

---

## 제약 조건 (Constraints)

- 기존 Redis 분산락·`BookingStatus.canTransitTo` 전이 체계 재사용.
- 위약 정책은 **별도 도메인 정책 객체**에 캡슐화(UseCase if-throw 금지). 부분환불은 기존 게이트 재사용.
- Waitlist는 독립 Entity(`@ManyToMany` 금지). audit 6컬럼·soft delete. Hexagonal + Rich Domain.

---

## 영향 범위 (Scope)

| 레포 | 변경 유형 | 설명 |
|---|---|---|
| backend | 신규 | `domain/booking/` Waitlist·CancellationPolicy, 승계 `@TransactionalEventListener` |
| backend | 수정 | `BookingStatus`(NO_SHOW)·`canTransitTo`, `refundBooking` 위약 연계, `aggregateFacilityKpi` 노쇼 집계 |
| mobile / web(B2C) | 신규/수정 | 대기 등록·순번·반복 예약 화면 |
| 이벤트 | 활용 | `BookingCancelledEvent`(in-process) AFTER_COMMIT 리스너로 승계 |

데이터 모델: 신규 `waitlists`, `booking_penalties`. `bookings.status`에 NO_SHOW.

### 확인된 누락 선행 티켓 (검수 도출)
| 제목 | 이유 |
|---|---|
| [DB] waitlists + NO_SHOW + booking_penalties 마이그레이션 | FR-00 — 공통 병목 |
| [BE] Waitlist 상태 enum + canTransitTo | Rich Domain 선행 |

---

## 오픈 이슈 (Open Issues)

| # | 질문 | 담당 | 기한 |
|---|------|------|------|
| 1 | 대기 승계 확정 유예 시간 (몇 분?) + 미응답 시 다음 순번 승계 동시성 책임 | PO/기술 리드 | FR-01 전 |
| 2 | 취소 위약율·노쇼 페널티 정책 (법무 검토) | PO/Legal | FR-02 전 |
| 3 | NO_SHOW 전이 규칙 (REFUNDED 부분환불 연계 여부) | 기술 리드 | FR-02 전 |
| 4 | 누적 노쇼 페널티 카운팅 윈도우·해제 조건 | PO | FR-02 전 |
| 5 | 반복 예약 부분성공 트랜잭션 경계 + 최대 횟수 상한 | 기술 리드/PO | FR-03 전 |

---

## 마일스톤

| 단계 | 내용 | 목표일 |
|------|------|--------|
| v0.0 (선행) | FR-00 — 스키마·enum | TBD |
| v1.0 | FR-01 + FR-04 — 대기열 + 승계 알림 | TBD |
| v1.1 | FR-02 — 노쇼/취소 위약 + 부분환불 (Legal 후) | TBD |
| v1.2 | FR-03 — 반복 예약 | TBD |

---

## 검수 이력 (prd-reviewer)

| 일자 | 판정 | Must Fix | 반영 |
|---|---|---|---|
| 2026-05-31 | NEEDS_REVISION | M1 booking.cancelled Kafka 토픽 아님(in-process) / M2 Payment 부분환불 연계 누락·비목표 충돌 / M3 NO_SHOW 전이·KPI 산정 누락 / M4 RefundPolicyViolationException은 전이 가드(정책 아님) | 4건 전부 반영 — in-process 리스너 명시, PaymentRefundGateway 재사용, NO_SHOW 전이+KPI 전환, 별도 위약 정책 객체, 선행 티켓 |
| 2026-05-31 | **APPROVED** (재검수) | M1~M4 전부 해소 확인 (코드 인용 전수 정확) | 구현 착수 가능 |

## Document History

| 날짜 | 변경 내용 | 작성자 |
|---|---|---|
| 2026-05-31 | /prd 초안 → prd-reviewer Must Fix 4건 반영 | biuea3866 |

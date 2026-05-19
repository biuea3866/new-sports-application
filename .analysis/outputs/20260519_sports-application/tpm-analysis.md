# TPM 분석 — Sports Application (MyLifeSports 리팩토링 + 신규 도메인)

> 작성일: 2026-05-19
> 입력: [docs/prd/sports-application-prd.md](../../../docs/prd/sports-application-prd.md)
> 기반 산출물: [docs/legacy-analysis/MyLifeSports.md](../../../docs/legacy-analysis/MyLifeSports.md), [docs/tickets/INDEX.md](../../../docs/tickets/INDEX.md), [docs/tickets/](../../../docs/tickets/)
> prd-reviewer 2차 APPROVE (Critical 4 + Major 6 반영 완료)

본 파일은 `/feature` 파이프라인 표준 경로의 TPM 산출물 통합 뷰입니다. 상세 티켓 본문은 `docs/tickets/<CATEGORY>/<ID>.md`를 참조합니다.

---

## 영향 서비스 목록 (단일 레포, 3 하위 디렉토리)

| 디렉토리 | 역할 | 스택 |
|---|---|---|
| `backend/` | Kotlin/Spring Boot 모놀리스 (9개 도메인) | Kotlin 1.9 + Spring Boot 3.x + Gradle KTS |
| `web/` | Next.js 14 웹 클라이언트 + BFF | TypeScript 5 + Tailwind + shadcn/ui |
| `mobile/` | React Native + Expo SDK 51 앱 | TypeScript 5 + expo-router |

루트는 단일 git 레포 (`new-sports-application.git`). PR은 `feature/<CAT-NN>` 브랜치 단위.

---

## API 변경 목록

전부 **신규** (그린필드). 기존 BE 없음.

### BE (백엔드)

| 도메인 | 신규 엔드포인트 |
|---|---|
| Auth/User | `POST /users/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, `POST /admin/users/{id}/roles/{role}`, `DELETE /admin/users/{id}/roles/{role}` |
| Facility | `GET /facilities`, `GET /facilities/{id}`, `GET /facilities/stats/gu-type` |
| Booking | `POST /facilities/{id}/slots`, `GET /facilities/{id}/slots`, `DELETE /slots/{id}`, `POST /bookings`, `GET /bookings/me`, `GET /bookings/{id}`, `DELETE /bookings/{id}` |
| Payment | `POST /payments` (Idempotency-Key 헤더), `GET /payments/me`, `GET /payments/{id}` |
| Ticketing | `GET /events`, `GET /events/{id}`, `POST /events/{id}/seats/select`, `POST /ticket-orders`, `GET /tickets/me`, `GET /tickets/{id}` |
| Goods | `GET /products`, `GET /products/popular`, `GET /products/{id}`, `GET /cart/me`, `POST /cart/items`, `PATCH /cart/items/{id}`, `DELETE /cart/items/{id}`, `POST /goods-orders`, `GET /goods-orders/me`, `GET /goods-orders/{id}` |
| Post | `POST /posts`, `GET /posts`, `GET /posts/{id}`, `DELETE /posts/{id}`, `POST /posts/{id}/comments`, `GET /posts/{id}/comments`, `DELETE /comments/{id}` |
| Message | `POST /rooms`, `GET /rooms/{id}`, `GET /rooms/me`, `DELETE /rooms/{id}`, `POST /rooms/{id}/messages`, `GET /rooms/{id}/messages` |
| Notification | `GET /notifications/me`, `GET /notifications/{id}`, `PATCH /notifications/{id}/read`, `GET /notifications/me/unread-count`, **`POST /users/me/push-tokens`, `DELETE /users/me/push-tokens/{id}`, `GET /users/me/push-tokens`** |

총: 신규 **40+개** / 수정 0개 / 파괴적 0개.

### Web BFF

모든 BE 엔드포인트의 `/api/*` mirror + httpOnly 쿠키 기반 인증 처리.

### Mobile

BE API 직접 호출 (BFF 미경유). JWT는 expo-secure-store 보관.

---

## Kafka 토픽 변경

전부 **신규**. 토픽 명명 규약: `<도메인>.<이벤트>.v<버전>`.

| 토픽 | Producer | Consumer | 페이로드 핵심 필드 |
|---|---|---|---|
| `payment.completed.v1` | Payment | Booking, Ticketing, Goods, Notification | paymentId, orderType, orderId, amount, paidAt, idempotencyKey |
| `payment.failed.v1` | Payment | Booking, Ticketing, Goods | paymentId, orderType, orderId, reason, failedAt |
| `booking.confirmed.v1` | Booking | Notification | bookingId, userId, facilityId, facilityName, slotAt, amount |
| `ticket.issued.v1` | Ticketing | Notification | ticketIds[], userId, eventId, eventTitle, venue, startsAt, seats[{section, rowNo, seatNo, price}] |
| `goods.stock.changed.v1` | Goods | Goods(캐시 invalidate), Notification(V2) | productId, delta, reason, occurredAt |
| `notification.requested.v1` | (V1은 Spring 이벤트로 처리, Kafka 미사용) | NotificationDomainService | recipientId, channel, templateId, payload |

총: 신규 5개 (`notification.requested.v1`은 V1 Kafka 미사용 — Spring 이벤트로 처리).

---

## 티켓 목록 (총 75건)

| 카테고리 | 건수 | 마일스톤 | 상세 |
|---|---|---|---|
| INFRA | 7 | M0 | [INFRA/](../../../docs/tickets/INFRA/) |
| AUTH | 6 | M1 | [AUTH/](../../../docs/tickets/AUTH/) |
| FACILITY | 4 | M2 | [FACILITY/](../../../docs/tickets/FACILITY/) |
| BOOKING | 5 | M3 | [BOOKING/](../../../docs/tickets/BOOKING/) |
| PAYMENT | 5 | M4 | [PAYMENT/](../../../docs/tickets/PAYMENT/) |
| TICKETING | 7 | M5 | [TICKETING/](../../../docs/tickets/TICKETING/) |
| GOODS | 7 | M6 | [GOODS/](../../../docs/tickets/GOODS/) |
| POST | 4 | M7 | [POST/](../../../docs/tickets/POST/) |
| MESSAGE | 3 | M7 | [MESSAGE/](../../../docs/tickets/MESSAGE/) |
| NOTIFICATION | 6 | M8 | [NOTIFICATION/](../../../docs/tickets/NOTIFICATION/) |
| WEB | 12 | M-Web | [WEB/](../../../docs/tickets/WEB/) |
| MOBILE | 9 | M-Mobile | [MOBILE/](../../../docs/tickets/MOBILE/) |

### 사이즈 분포

- **S(~200줄)**: INFRA-01,02,03,04,05,07, AUTH-02,04,05,06, FACILITY-02,03, BOOKING-02,05, PAYMENT-03,05, TICKETING-02,07, POST-02,03,04, MESSAGE-02,03, NOTIFICATION-02 등 약 30건
- **M(~400줄)**: 대부분의 도메인/API 티켓 약 35건
- **L(~800줄)**: TICKETING-04(좌석 락), TICKETING-06(consumer+발권), GOODS-05(주문+재고), GOODS-06(보상), BOOKING-03(락+API), NOTIFICATION-06(Expo Push), WEB-05(좌석맵+구매), MOBILE-05(좌석맵+구매), MOBILE-08(푸시+딥링크), MOBILE-09(오프라인+성능) 약 10건

---

## 의존 그래프 (DAG)

다음은 `docs/tickets/INDEX.md`에서 추출한 의존 관계. 메인 오케스트레이터가 wave 위상정렬에 사용.

### Wave 1 (초기 ready 셋 — 선행 없음)

| 티켓 | 후행 카운트 | 비고 |
|---|---|---|
| INFRA-01 | 4 (INFRA-02/03/04/05) | 모든 BE의 최상위 선행 |
| WEB-01 | 9 (WEB-02~09) | Web 트랙 진입점 |
| MOBILE-01 | 8 (MOBILE-02~09) | Mobile 트랙 진입점 |

→ Wave 1 = **3개 동시 스폰** (단일 어시스턴트 메시지)

### Wave 2

| 티켓 | 선행 | 후행 카운트 |
|---|---|---|
| INFRA-02 | INFRA-01 | 6 (도메인 BE) |
| INFRA-03 | INFRA-01 | 3 (Mongo 도메인) |
| INFRA-04 | INFRA-01 | 분산 락 사용 도메인 |
| INFRA-05 | INFRA-01 | INFRA-06 |
| WEB-09 | WEB-01 | (Web 단독) |

→ Wave 2 = **5개 동시 스폰**

### Wave 3+

INFRA-06(이벤트 publisher)·INFRA-07(예외 처리)·도메인 진입 티켓 다수. 메인 세션이 매 wave마다 위상정렬로 재계산.

### Fan-out 핵심 — PAYMENT-04

PAYMENT-04(`payment.completed/failed.v1` 발행)가 BOOKING-04 / TICKETING-06 / GOODS-06 / NOTIFICATION-03 4개 후행의 공통 선행. 의존 ≥ 3이지만 발행/구독 분리에 따른 필연적 병목으로 분할 불필요.

### 전체 의존 그래프

[docs/tickets/INDEX.md](../../../docs/tickets/INDEX.md)의 mermaid `flowchart LR` 섹션 참조. 본 파일에서 중복 작성하지 않음.

---

## 티켓 상세

각 티켓의 작업 범위·완료 기준·테스트 케이스(3계층 Unit/Repository/Scenario)는 [docs/tickets/<CATEGORY>/<ID>.md](../../../docs/tickets/)를 참조.

본 통합 파일은 위 메타데이터만 다루며, 티켓 본문 중복 작성은 단일 진실 원천(SSOT) 원칙에 따라 회피.

---

## 검수 이력

- **2026-05-19** prd-reviewer 1차 — REQUEST_CHANGES (Critical 4 + Major 6 + Minor 4)
- **2026-05-19** Critical 4 + Major 6 모두 반영
- **2026-05-19** prd-reviewer 2차 — **APPROVE** (신규 발견 N-M1/N-m1~m3은 구현 차단 사유 아님, 구현 중 finally 블록 1줄 수정으로 해결)

---

## 다음 단계

`/feature` 파이프라인 Step 1-D (TDD 작성)로 진입. TDD 산출물은 `.analysis/outputs/20260519_sports-application/tdd.md`에 작성.

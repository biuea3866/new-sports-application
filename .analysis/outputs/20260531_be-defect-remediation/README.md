# BE 결함 수정 — 티켓 분해 (2026-05-31)

2026-05-31 BE 코드 검토에서 발견된 결함 18건을 수정하기 위한 23개 티켓. 코드 검토 산출물이며, 재발 방지 가드는 PR #184(머지됨)로 별도 처리됨.

## 설계 결정 (확정)

- **결제 완료→주문 확정은 완전 비동기(웹훅 기반)**: 주문 PENDING 유지, `PaymentDomainService.confirmWebhook`이 `OrderType`별 주문 확정 + `payment.completed.v1` AFTER_COMMIT 발행. 동기 `when(payment.status)` 분기 전부 제거.
- 핵심 발견: `payment.completed.v1`·`booking.confirmed.v1`·`ticket.issued.v1` 토픽은 Consumer(`NotificationEventWorker`)가 이미 구독 중이고 **Producer만 비어 있음**. #1은 "토픽 신설"이 아니라 "Producer 발행 + 도메인 이벤트 정의".

## 의존 DAG / 웨이브 (Single Writer 충돌 해소 후)

| Wave | 티켓 | 너비 |
|---|---|---|
| **Wave 1** | BE-01, BE-02, BE-03, DB-01, DB-02, BE-08, BE-10, BE-11, BE-12, BE-15, BE-17, BE-18, BE-21, INFRA-01, INFRA-02 | 15 |
| **Wave 2** | BE-04, BE-05, BE-06b, BE-14b, BE-20 | 5 |
| **Wave 3** | BE-13, BE-16, BE-19 | 3 |

평균 wave 너비 ≈ 7.7 (4인 팀 목표 ≥3 충족). 병목: `PaymentDomainService.kt`(BE-01→BE-14b→BE-13 직렬), `BookingDomainService.kt`(BE-06b 단일 writer 통합).

### 선행 병목(공통 산출물) 추출
- **BE-01** payment 도메인 이벤트 + confirmWebhook 확정 코어 → BE-04·05·06b·13·14b가 의존
- **BE-02** OrderConfirmationGateway ACL → BE-04·05·19 의존
- **BE-03** 공통 enum → BE-20 의존
- **DB-01/DB-02** → BE-06b/BE-13 의존

## 티켓 ↔ 결함 매핑

| 티켓 | 결함# | 제목 | Wave |
|---|---|---|---|
| BE-01 | #1 | payment 도메인 이벤트 + confirmWebhook 주문확정·발행 코어 | 1 |
| BE-02 | #14 | OrderConfirmationGateway ACL 정의 | 1 |
| BE-03 | #15 | 공통 enum (UserRole/Currency/PgEventType) | 1 |
| DB-01 | #2 | bookings 활성예약 capacity DB 방어 마이그레이션 | 1 |
| DB-02 | #8 | payments pg_transaction_id unique 마이그레이션 | 1 |
| BE-08 | #3 #4 | ticketing aggregate 고아 차단 (cancelOrder+deleteEvent) | 1 |
| BE-10 | #5 | Post→Comment 고아 차단 | 1 |
| BE-11 | #6 | GoodsOrder→Item 취소 전파 | 1 |
| BE-12 | #7 | 낙관락 충돌 409 처리 | 1 |
| BE-15 | #11 | OSIV — notification/booking Entity 반환→DTO | 1 |
| BE-17 | #12 | CartRepositoryImpl 순수성 (조회 시그니처 정비) | 1 |
| BE-18 | #13 | CartDomainService self-validation→Entity 위임 | 1 |
| BE-21 | #16 | 상태전이 가드 (Notification.markFailed, Facility.ownerUserId) | 1 |
| INFRA-01 | #17 | weather 패키지 선언 정렬 (detekt 빌드 차단 해소) | 1 |
| INFRA-02 | #18 | ArchUnit 동어반복 폐기 + Kover 게이트 | 1 |
| BE-04 | #1 | goods 주문 비동기 확정 전환 | 2 |
| BE-05 | #1 | ticketing 주문 비동기 확정 전환 | 2 |
| BE-06b | #1 #2 #9 #10 | booking 통합 (confirmBooking+AFTER_COMMIT+오버부킹+refund 분리) | 2 |
| BE-14b | #9 | payment prepare 트랜잭션 분리 | 2 |
| BE-20 | #15 | 매직 문자열 enum 적용 (호출부 갱신) | 2 |
| BE-13 | #8 | webhook 동시 첫 도착 멱등 | 3 |
| BE-16 | #11 | OSIV — ticketing createPendingOrder/confirmOrder DTO화 | 3 |
| BE-19 | #14 | payment 결합 ACL 통일 | 3 |

## Open Questions — 결정 완료 (2026-05-31)

| # | 티켓 | 결정 |
|---|---|---|
| OQ-1 | DB-01/BE-06b | **slot row 비관락(`SELECT FOR UPDATE` + `@Lock(PESSIMISTIC_WRITE)`)**. capacity>1 가능 → 부분 unique 불가. DB-01은 count 쿼리 보강 인덱스만, 직렬화는 BE-06b |
| OQ-2 | BE-04/05/06b | **주문 상태 조회 API(폴링)** 제공. 생성 응답은 PENDING 고정, FE는 `GET .../{id}`로 PENDING→CONFIRMED 폴링. (FE 인계 필요) |
| OQ-3 | INFRA-02 | Kover **리포트만 활성화, 게이트(koverVerify) 미적용**. 임계 강제는 실측 후 후속 티켓 |
| OQ-4 | INFRA-01 | presentation `ForecastResponse`는 **참조처 0건 데드 코드 → 삭제** |
| OQ-5 | INFRA-02 | `SportsApplication 루트 패키지` 단언 **보존**, 동어반복 4건만 삭제 |
| OQ-6 | BE-20 | Spring Security **SpEL 문자열은 유지**, enum화는 프로그래밍 비교(UserDomainService 등)에만 |

## 비고

- 본 23건은 결함 **수정** 티켓. 재발 방지 가드(harness-rules·ArchUnit·컨벤션·에이전트)는 PR #184로 머지 완료.
- 티켓 md는 ticket-guide.md 형식(작업내용/다이어그램/3계층 테스트케이스). AC·파일목록 미포함.
- Jira 등록은 검토 후 `/jira-ticket` 스킬로.

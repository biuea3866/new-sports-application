# 결함 요약 (run2 — clean)

`/qa --full-regression` Step 3 산출. run1 환경 오염 정정 후 재실행 결과 기준.

## 인덱스

| ID | layer | severity | auto-fix | source | 제목 |
|---|---|---|---|---|---|
| DEF-001 | BE | Major | ✅ | E2E-06-R02 | POST /goods-orders 동일 Idempotency-Key 재호출 시 신규 order id 발급 (멱등 미구현) |
| DEF-002 | BE | Major | ✅ | E2E-04-05 | GET /payments/me?status=PAID 호출 시 500 |
| DEF-003 | BE | Major | ✅ | E2E-04-E01 | POST /payments Idempotency-Key 헤더 누락 시 500 |
| DEF-004 | BE | Major | ✅ | E2E-05-E02 | POST /ticket-orders Idempotency-Key 헤더 누락 시 500 |
| DEF-005 | BE | Major | ✅ | E2E-05-E05 | POST /events/{id}/seats/select 빈 seatIds 시 500 |
| DEF-006 | AMBIGUOUS | Minor | ❌ | E2E-01-E01, E2E-01-E02 | 가입 검증 spec 400 vs BE 422 |
| DEF-007 | AMBIGUOUS | Minor | ❌ | E2E-03-03 | bookings/me 응답 키 `bookings` vs spec `content/items` |
| DEF-008 | AMBIGUOUS | Minor | ❌ | E2E-08-03, E2E-08-E03 | unread-count 응답 키 `count` vs spec `unreadCount` |
| DEF-009 | AMBIGUOUS | Minor | ❌ | E2E-08-E04 | 빈 메시지 POST spec assertion 허용 목록 누락 |
| DEF-010 | BE | Major | ✅ | LOAD-03 | POST /bookings 부하 시 전수 500 (error rate 100%) |
| DEF-011 | AMBIGUOUS | Minor | ❌ | LOAD-02 | http_req_failed threshold가 5xx 외 비즈니스 응답까지 집계 |

## 집계

| layer | Critical | Major | Minor | 합계 |
|---|---|---|---|---|
| BE | 0 | 6 | 0 | 6 |
| FE | 0 | 0 | 0 | 0 |
| INFRA | 0 | 0 | 0 | 0 |
| AMBIGUOUS | 0 | 0 | 5 | 5 |
| 합계 | 0 | 6 | 5 | **11** |

| auto-fix-eligible | 건수 |
|---|---|
| true | 6 |
| false | 5 |

## 자동 호출 대상 (Step 4)

- be-implementer: DEF-001, DEF-002, DEF-003, DEF-004, DEF-005, DEF-010 (6건 — Major BE)
- fe-implementer: 없음

## 사람 검토 필요

- AMBIGUOUS spec 결함: DEF-006, DEF-007, DEF-008, DEF-009
  - 공통 결정 사항: BE 응답 컨벤션(에러 코드 400/422, Page 응답 키, 컬렉션 키 이름)을 확정한 뒤 spec 또는 BE 한쪽 일괄 수정. 4건이 모두 BE 응답 컨벤션 미합의에서 비롯되므로 일괄 결정 권장.
- AMBIGUOUS 시나리오 정의 결함: DEF-011
  - 공통 헬퍼 `qa/load/k6/lib/metrics.js` 또는 스크립트별 override로 5xx 전용 임계만 부여하도록 조정.

## DEF-010 사전 점검 (be-implementer 위임 전 확인 권장)

DEF-010은 시드(`qa/load/seeds/booking-create.sql`) 미적용 가능성이 있음. be-implementer에 위임 전:
1. `qa/load/seeds/booking-create.sql`이 부하 실행 직전 적용됐는지 확인
2. 적용됐는데도 500이면 BE 결함 → be-implementer 진행
3. 적용 안 됐다면 시드 적용 후 재실행으로 재현성 확인 → BE 결함 아닐 수 있음

## 다음 액션

1. DEF-001 ~ DEF-005, DEF-010 → be-implementer 자동 호출 (워크트리 격리). DEF-003/DEF-004는 동일 패턴(`Idempotency-Key` 헤더 부재 → 500)이므로 공통 처리 후보 — be-implementer가 한 PR/티켓 안에서 묶을 수 있음.
2. DEF-006/DEF-007/DEF-008/DEF-009 → 사람이 BE 응답 컨벤션 일괄 결정 후 spec 또는 BE 수정.
3. DEF-011 → 사람이 부하 측정 정의 조정 (공통 헬퍼 또는 스크립트별 override).

## 산출물 경로

```
.analysis/outputs/qa/20260524_full-regression/defects/
├── _summary.md                                                       (본 파일)
├── DEF-001-goods-orders-idempotency-not-implemented.md
├── DEF-002-payments-me-status-filter-500.md
├── DEF-003-payments-missing-idempotency-key-500.md
├── DEF-004-ticket-orders-missing-idempotency-key-500.md
├── DEF-005-events-seats-select-empty-seatids-500.md
├── DEF-006-auth-register-spec-expects-400-but-be-returns-422.md
├── DEF-007-bookings-me-response-key-bookings-not-content.md
├── DEF-008-notifications-unread-count-key-count-not-unreadcount.md
├── DEF-009-notification-message-spec-tocontain-direction.md
├── DEF-010-booking-create-throughput-post-bookings-500.md
└── DEF-011-load-02-http-req-failed-threshold-misdefined.md
```

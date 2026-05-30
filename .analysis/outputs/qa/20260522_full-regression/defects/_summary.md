# 결함 요약 — 2026-05-22 full-regression

## 회귀 결과 요약

| 항목 | 값 |
|---|---|
| E2E 총 테스트 | 90 (Pass 60 / Fail 19 / Skip 10) |
| 부하 시나리오 | 3 (LOAD-01 PASS / LOAD-02·03 SKIP) |
| 산출 결함 | 9건 (DEF-001 기존 + DEF-002~DEF-010 신규) |
| auto-fix 대상 (BE/FE + Critical/Major) | 7건 |
| 사람 검토 대상 (INFRA / AMBIGUOUS / Minor) | 2건 |

## 결함 목록

| ID | 제목 | layer | severity | auto-fix | source |
|---|---|---|---|---|---|
| DEF-001 | StockCustomRepository query derivation 실패 (BE 기동 차단) | BE | Critical | true | (Step 0 환경) |
| DEF-002 | /portal SSR 500 — BACKEND_URL 미설정 시 be-client 모듈 throw | FE | Critical | true | E2E-07-01~05·R01·E01·E02·E03 (9건) |
| DEF-003 | Bean Validation 422 vs 400 매핑 | BE | Critical | true | E2E-01-E01·E02 |
| DEF-004 | Idempotency-Key 누락 500 vs 400 (Missing*HeaderException 미처리) | BE | Critical | true | E2E-04-E01, E2E-05-E02 |
| DEF-005 | GET /bookings/me 응답 페이지 필드명 (bookings → content) | BE | Critical | true | E2E-03-03 |
| DEF-006 | GET /payments/me?status=PAID 500 (status 쿼리 매핑 오류) | BE | Critical | true | E2E-04-05 |
| DEF-007 | 빈 seatIds POST /events/{id}/seats/select 500 | BE | Major | true | E2E-05-E05 |
| DEF-008 | UnreadCountResponse 필드명 (count → unreadCount) | BE | Major | true | E2E-08-03, E2E-08-E03 |
| DEF-009 | 빈 메시지 검증 404 vs 400 (RoomNotFound 우선 / 시드 부재) | INFRA | Minor | false | E2E-08-E04 |
| DEF-010 | k6 부하 auth fixture 누락 (LOAD-02/03 SKIP) | INFRA | Major | false | LOAD-02, LOAD-03 |

## 자동 호출 대상 (be-implementer / fe-implementer)

`/qa` Step 4가 워크트리에서 자동 호출하는 결함:

| 결함 ID | 대상 에이전트 | 한 줄 요약 |
|---|---|---|
| DEF-001 | be-implementer | Stock 엔티티에 `ownerId` 없음 — Spring Data fragment custom impl 미인식으로 query derivation 실패. BE 자체 기동 불가 (이미 PR #106 진행 중) |
| DEF-002 | fe-implementer | `web/lib/server/be-client.ts`가 `BACKEND_URL` 미설정 시 top-level throw → /portal 진입 모두 SSR 500. lazy 평가 또는 env 주입 경로 정리 |
| DEF-003 | be-implementer | `GlobalExceptionHandler.handleValidationException`이 422 반환 → 400으로 변경 |
| DEF-004 | be-implementer | `MissingRequestHeaderException` 미처리로 500. ExceptionHandler 보강 또는 컨트롤러 `required=false` 일관화 |
| DEF-005 | be-implementer | `ListBookingsResponse.bookings` → `content`로 필드명 변경 |
| DEF-006 | be-implementer | `GET /payments/me?status=PAID` 500 — BE 로그로 진단 후 enum/converter/handler 중 1개 fix |
| DEF-007 | be-implementer | `SelectSeatsRequest.seatIds`에 `@NotEmpty` + 컨트롤러 `@Valid` 추가. DEF-003 fix와 함께 적용 권장 |
| DEF-008 | be-implementer | `UnreadCountResponse.count` → `unreadCount`로 필드명 변경 |

## 사람 검토 대상

| 결함 ID | 사유 |
|---|---|
| DEF-009 | INFRA + Minor. BE DTO에 `@NotBlank` 부착 여부 확인 후 분류 재검토 — BE 결함일 가능성도 있음 |
| DEF-010 | INFRA. auth.js endpoint/payload 정정 vs BE seed 추가 vs X-User-Id 헤더 모델 — 측정 정책 결정 필요 |

## 결함 분포

| layer | 건수 | severity 분포 |
|---|---|---|
| BE | 7 (DEF-001·003·004·005·006·007·008) | Critical 5 / Major 2 |
| FE | 1 (DEF-002) | Critical 1 |
| INFRA | 2 (DEF-009·010) | Major 1 / Minor 1 |
| AMBIGUOUS | 0 | — |

## 결함 묶음 메모

- **/portal SSR 500** 9건은 같은 `be-client.ts` 모듈 로드 결함 → DEF-002 1건으로 묶음 (사람이 한 PR로 fix하기 자연스러운 단위)
- **Idempotency-Key 누락** 2건(payment + ticket-orders)은 컨트롤러 시그니처가 달라 두 곳 수정 필요하지만 같은 ExceptionHandler 보강 + `@RequestHeader` required 정리 한 줄기 → DEF-004 1건으로 묶음
- **UnreadCountResponse** E2E-08-03·E03 2건은 같은 응답 DTO → DEF-008 1건
- **Validation 422→400** 2건(E01·E02)은 같은 ExceptionHandler → DEF-003 1건
- **LOAD-02·LOAD-03** 둘 다 같은 `qa/load/k6/lib/auth.js` 헬퍼 결함 → DEF-010 1건

## 다음 단계

`/qa` Step 4 (메인 세션에서 트리거):
- DEF-002 → fe-implementer 워크트리 자동 생성
- DEF-003 ~ DEF-008 → be-implementer 워크트리 자동 생성 (DEF-003 + DEF-007은 의존성 — DEF-003 먼저 또는 동일 PR)
- DEF-001은 PR #106 진행 중 (기존)
- DEF-009, DEF-010은 사람 검토 큐로 백로그

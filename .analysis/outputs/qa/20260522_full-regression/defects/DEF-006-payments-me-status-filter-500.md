# DEF-006 GET /payments/me?status=PAID 500 — status 쿼리 파라미터 적용 시 내부 오류

## 메타
- layer: BE
- severity: Critical
- auto-fix-eligible: true
- source-scenario: E2E-04-05
- detected-at: 2026-05-22T02:20:00+09:00
- environment: docker-compose.qa.yml (commit `e810357`, branch `feat/qa-pipeline`)
- related-pr: none
- related-ticket: none

## 분류 근거
- E2E error-context (`payment-create-list-E2E-04-13638-*-chromium/error-context.md`):
  > spec L103-110: `expect(res.status()).toBe(200)` → 실제 500
- 동일 엔드포인트의 `?status` 없는 호출(E2E-04-04)은 200 통과 (e2e-run.log 확인) → `status` 쿼리 파라미터를 추가했을 때만 500
- BE 측 ApplicationException 또는 QueryDSL 매핑 오류 → layer: BE
- 결제 목록은 사용자가 자기 결제 내역 조회하는 핵심 플로우 → Critical

## 재현 단계
1. BE 기동
2. `GET /payments/me?status=PAID` 호출 (`X-User-Id: 1` 헤더)
3. **500 응답** (200 + PAID로 필터된 결과 기대)
4. 비교 — `GET /payments/me` (status 없이) 호출 시 200 응답 정상 (E2E-04-04 통과)

## 기대 동작
HTTP 200 + Spring Data `Page<PaymentResponse>` 형식, `content` 배열의 모든 항목 `status == "PAID"`.

## 실제 동작
HTTP 500. BE 로그 미수집(QA 환경에 BE 로그 캡처 부재) — 원인 추정 단서:
- `PaymentApiController.kt:55`: `@RequestParam(required = false) status: PaymentStatus?` — Spring이 `"PAID"` 문자열을 `PaymentStatus` enum으로 변환. enum에 `PAID` 값이 없으면 `MethodArgumentTypeMismatchException` → 미처리 시 500
- `PaymentCustomRepositoryImpl.kt:38-47`: `status?.let { predicate.and(payment.status.eq(it)) }` — enum 비교는 QueryDSL에서 일반적으로 동작. JSON 컬럼이거나 `@Convert` 적용 시 문제 가능
- `Sort.by(Sort.Direction.DESC, "createdAt")` (Controller L66) → QueryDSL `.orderBy(payment.createdAt.desc())` 정렬은 정상이나 Spring Data가 추가로 정렬을 시도하면 unknown property 가능

## 영향 범위
- 영향 사용자: 결제 내역에서 상태 필터를 사용하는 모든 사용자 (마이페이지 > 결제 내역 > 상태별 필터)
- 영향 화면/엔드포인트: `GET /payments/me?status=*` — `status` 외 `paidAtFrom`/`paidAtTo` 도 같은 패턴인지 확인 필요
- 데이터 영향: 없음 (read only)

## 아티팩트
- [E2E-04-05 error-context](../artifacts/playwright-output/payment-create-list-E2E-04-13638-e-status-PAID-—-결과는-모두-PAID-chromium/error-context.md)

## 의심 코드 경로

| 파일 | 라인 | 역할 |
|---|---|---|
| `backend/src/main/kotlin/com/sportsapp/presentation/payment/PaymentApiController.kt` | 52-69 | `status: PaymentStatus?` 파라미터 — Spring 변환 단계 또는 UseCase 호출에서 오류 가능 |
| `backend/src/main/kotlin/com/sportsapp/application/payment/PaymentCriteria.kt` | (확인 필요) | criteria DTO에 status 매핑 |
| `backend/src/main/kotlin/com/sportsapp/infrastructure/persistence/payment/PaymentCustomRepositoryImpl.kt` | 38-47 | `status?.let { predicate.and(payment.status.eq(it)) }` QueryDSL 매핑 |
| `backend/src/main/kotlin/com/sportsapp/domain/payment/PaymentStatus.kt` | (확인 필요) | enum 정의에 `PAID` 값 존재 여부 |
| `backend/src/main/kotlin/com/sportsapp/domain/payment/Payment.kt` | (확인 필요) | `status` 컬럼이 `@Enumerated(EnumType.STRING)` 인지, `@Convert` 인지 |

가설:
- A) `PaymentStatus` enum에 `PAID` 값이 없음 — `MethodArgumentTypeMismatchException` 발생. ExceptionHandler 미정의 → 500
- B) `Payment.status` 컬럼이 INT 또는 사용자 정의 converter — QueryDSL 비교가 실패
- C) UseCase·Repository 체인 어딘가에서 NPE — 빈 결과 + status 결합 시점

## 자동 수정 지시
대상 에이전트: be-implementer

작업 범위:
- 결함 한정 — `GET /payments/me?status=PAID` 500 원인 1건만 수정. 다른 list 엔드포인트나 enum 정의 광범위 리뷰 금지 (CLAUDE.md §3 정밀한 수정)
- TDD 사이클:
  1. **RED**: `PaymentApiControllerTest.kt` 또는 새 통합 테스트에 `status=PAID` 쿼리 파라미터로 호출 시 200 응답을 검증하는 케이스 작성 — 현재 시점 500이 떠야 함
  2. **GREEN**: 진단 후 fix — enum 값 추가 / converter 정정 / ExceptionHandler 보강 중 결함에 한정된 1개
  3. **GREEN 검증**: E2E-04-05 통과 + `?status=PENDING`, `?status=FAILED` 등 다른 값도 동일하게 200 (회귀 케이스 1건 이상)
- 테스트 위치 제안: `backend/src/test/kotlin/com/sportsapp/presentation/payment/PaymentApiControllerTest.kt`
- 예상 변경 파일 수: 1~2개
- **반드시 점검**: BE 로그에서 실제 스택트레이스 확인 후 진단. 로그 없이 추측으로 fix 금지

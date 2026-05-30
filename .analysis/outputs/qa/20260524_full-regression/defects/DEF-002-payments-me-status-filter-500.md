# DEF-002 GET /payments/me?status=PAID 호출 시 500 Internal Error

## 메타
- layer: BE
- severity: Major
- auto-fix-eligible: true
- source-scenario: E2E-04-05
- detected-at: 2026-05-24T18:33:14+09:00
- environment: docker-compose.qa.yml (BE commit 74e82bf)
- related-pr: none
- related-ticket: none

## 분류 근거
- `logs/e2e-regression.log:65` — `✘ 62 ... E2E-04-05 GET /payments/me?status=PAID — 결과는 모두 PAID (25ms)`
- `logs/e2e-regression.log:282-292`:
  ```
  Expected: 200
  Received: 500
  > 106 |       expect(res.status()).toBe(200);
  ```
- 정상 응답(200)이 와야 할 조회 API가 500을 반환 → BE 내부 오류. spec은 200 단언일 뿐 의도된 검증 응답이 아님. **layer: BE** / severity 상속(Major).

## 재현 단계
1. 결제 데이터가 시드된 user 로그인 (`X-User-Id` 헤더 사용)
2. `GET /payments/me?status=PAID` 호출
3. 응답 상태 코드 확인

## 기대 동작
HTTP 200 + body의 모든 항목이 `status=PAID`인 페이지 응답.

## 실제 동작
HTTP 500 Internal Error. `status` 쿼리 파라미터 처리 경로에서 예외 발생으로 추정 (enum 매핑 / nullable 쿼리 처리 결함 가능성).

## 영향 범위
- 영향 사용자: 결제 내역 필터 조회를 사용하는 모든 사용자
- 영향 화면/엔드포인트: `GET /payments/me?status=*`
- 데이터 영향: 없음 (조회 API)

## 아티팩트
- [trace](../artifacts/payment-create-list-E2E-04-13638-e-status-PAID-—-결과는-모두-PAID-chromium/trace.zip)
- [regression log](../logs/e2e-regression.log) (line 65, 278~302)

## 의심 코드 경로
- `backend/src/main/kotlin/com/sportsapp/presentation/payment/PaymentApiController.kt` — `status` 쿼리 파라미터 바인딩 (enum, nullable)
- `backend/src/main/kotlin/com/sportsapp/infrastructure/persistence/payment/` 하위 QueryDSL 구현 — status 조건 적용 시 NPE 또는 enum 변환 오류 가능성
- 동일 API의 status 파라미터 미지정 케이스(E2E-04-04)는 200 통과 → status 파라미터 처리 자체에 한정된 결함

## 자동 수정 지시
대상 에이전트: be-implementer
작업 범위:
- 결함 한정 — `GET /payments/me`의 `status` 파라미터 처리만 수정. 다른 필터(`paidAtFrom` 등)와 무관하게 진행.
- TDD 사이클: `GET /payments/me?status=PAID` 호출 시 200 + 모든 항목 status=PAID 단언하는 시나리오 테스트를 먼저 RED.
- 테스트 위치 제안: `backend/src/test/kotlin/com/sportsapp/.../PaymentApiControllerTest.kt` 또는 PaymentQueryUseCase 시나리오 테스트.
- 예상 변경 파일 수: 2개 (Controller 또는 Custom Repository + 테스트).

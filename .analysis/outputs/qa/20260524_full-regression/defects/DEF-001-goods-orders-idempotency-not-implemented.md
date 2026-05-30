# DEF-001 POST /goods-orders 동일 Idempotency-Key 재호출 시 신규 order id 발급 (멱등 미구현)

## 메타
- layer: BE
- severity: Major
- auto-fix-eligible: true
- source-scenario: E2E-06-R02
- detected-at: 2026-05-24T18:33:14+09:00
- environment: docker-compose.qa.yml (BE commit 74e82bf)
- related-pr: none
- related-ticket: none

## 분류 근거
- `logs/e2e-regression.log:42` — `✘ 39 ... E2E-06-R02 같은 Idempotency-Key 로 /goods-orders 재호출 시 동일 order id (1.3s)`
- `logs/e2e-regression.log:178-184` — Playwright 단언 결과:
  ```
  Expected: 1
  Received: 2
  > 163 |       expect(b2.id).toBe(b1.id);
  ```
- spec(`qa/e2e/specs/goods-cart-order.spec.ts:163`)이 두 응답의 order id 동치를 단언 → 1차 응답 id=1, 2차 응답 id=2로 서로 다른 row가 생성됨.
- HTTP는 정상 응답(202)이고 동일 키 매핑 row 조회 누락. **layer: BE** (멱등 처리 누락) / severity는 시나리오 md 상속(Major).

## 재현 단계
1. user 계정으로 `POST /goods-orders` 호출 (헤더 `Idempotency-Key: <고정 UUID>`)
2. 동일한 `Idempotency-Key`로 `POST /goods-orders` 즉시 재호출
3. 두 응답의 `id` 비교

## 기대 동작
동일 `Idempotency-Key`로 두 번 호출하면 새 주문이 생성되지 않고 기존 order id가 반환된다.

## 실제 동작
2회 호출 모두 새 row를 생성. 1차 `id=1`, 2차 `id=2` 반환. 단언 실패: `Expected: 1, Received: 2`.

## 영향 범위
- 영향 사용자: goods 주문 클라이언트 — 재시도 시 중복 결제·중복 주문 발생 가능
- 영향 화면/엔드포인트: `POST /goods-orders`
- 데이터 영향: 있음 — 동일 의도 1회 결제에 대해 중복 order row 생성

## 아티팩트
- [trace](../artifacts/goods-cart-order-E2E-06-go-641b3-ds-orders-재호출-시-동일-order-id-chromium/trace.zip)
- [regression log](../logs/e2e-regression.log) (line 42, 174~198)

## 의심 코드 경로
- `backend/src/main/kotlin/com/sportsapp/presentation/goods/` 하위 `GoodsOrderApiController.kt` — Idempotency-Key 헤더 수신 후 기존 row 조회 로직 누락 가능성
- `backend/src/main/kotlin/com/sportsapp/application/goods/` 하위 `*GoodsOrder*UseCase.kt` — 동일 키 사전 조회 분기 누락 가능성
- payments는 E2E-04-02가 통과(같은 키 → 같은 id)하므로 payment의 멱등 처리 패턴 참조 권장

## 자동 수정 지시
대상 에이전트: be-implementer
작업 범위:
- 결함 한정 — `POST /goods-orders` 멱등 처리만 구현. 인접 도메인(cart·product) 리팩토링 금지 (CLAUDE.md §3 정밀한 수정).
- TDD 사이클: 동일 Idempotency-Key 2회 호출 시 동일 id 반환을 검증하는 시나리오 테스트를 먼저 RED로 작성 → fix → GREEN.
- 테스트 위치 제안: `backend/src/test/kotlin/com/sportsapp/.../GoodsOrderScenarioTest.kt` 또는 기존 goods 시나리오 테스트 디렉토리.
- 예상 변경 파일 수: 2~3개 (Controller·UseCase·테스트).

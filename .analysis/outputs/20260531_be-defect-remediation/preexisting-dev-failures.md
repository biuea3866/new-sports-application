# 사전존재(origin/dev) 시나리오 실패 — 별도 결함 (이 브랜치 범위 밖)

baseline 교차검증(2026-06-04): `origin/dev` (361f3cd0) 워크트리에서 아래 3개 시나리오 클래스를 실행 → **12 tests, 6 failed**. `fix/be-defect-remediation` 의 변경과 무관하게 dev 에서 이미 실패. **이 브랜치의 회귀가 아님.**

| 클래스 | 추정 원인 | 분류 |
|---|---|---|
| `scenario/payment/PaymentWebhookScenarioTest` (4건) | `BaseJpaIntegrationTest` 가 Kafka autoconfig 를 제외 → `/payments/webhook` MVC 라우팅 미등록(`NoResourceFoundException`). 테스트 인프라(프로파일) 문제 | INFRA/test-harness |
| `scenario/payment/PaymentCreateScenarioTest` S-03 | amount=-100 입력이 기대 HTTP status 와 불일치(검증 갭) | BE 검증 |
| `scenario/goods/GoodsSellerApiScenarioTest` S-04 | stock 음수 입력이 기대 status 와 불일치(검증 갭) | BE 검증 |

## 영향

- 로컬 `push-test` 게이트는 `:backend:test` 전체를 돌리므로 위 사전존재 실패로 push 가 차단됨. 이는 dev 도 동일(로컬 기준).
- 따라서 `fix/be-defect-remediation` 자체는 단위테스트 green·OSIV/고아 0·clean 컴파일 상태지만, **dev 의 사전 red 를 해소하기 전엔 로컬 push-test 를 통과할 수 없음.**

## 권고

- 위 3건은 **별도 결함 티켓**으로 분리 (이 18-결함 remediation 범위 밖, baseline red).
- PaymentWebhookScenarioTest 는 테스트 하니스(Kafka autoconfig 제외) 수정이 핵심 — 운영 코드 결함 아닐 가능성.
- 검증 status 2건은 controller/request 검증(@Valid·도메인 예외→4xx 매핑) 추가로 해소.

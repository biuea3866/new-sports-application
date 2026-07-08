
---

## p0 수정 결과 (2026-06-01)

| # | 상태 | 커밋(브랜치 머지됨) |
|---|---|---|
| P-1 (PG-in-tx, goods/booking) | ✅ createPending+initiatePg 분리, PG tx 밖 | efbddef1 |
| P-1 (ticketing) | ✅ PurchaseTicketsUseCase 동일 패턴 | 92bba24e |
| P-2 (재고 영구손실) | ✅ PAYMENT_CANCELED→OrderConfirmationGateway.cancel()→cancelPendingOrder(재고복원), @Transactional | 8cd4a8e0 |
| P-3 (ticket 고아) | ✅ cancelOrder 시 ticket.softDelete 전파 | bad8d646 |
| P-4 (좌석 unlock 선행) | ✅ afterCommit unlock | bad8d646 |
| P-5 (post OSIV) | ✅ Comment read-only postId + getDetail @Transactional | df8485ea |
| P-6 (domain→app DTO) | ✅ ticketing TicketOrderResult / notification NotificationResult | 33c5e283, f75b8b2b |
| P-7 (@Transactional 누락) | ✅ goods cancelPendingOrder; booking confirm/refund는 호출부 tx 확인; message는 UseCase 단일경계 | b942cef4, 6b6187bb |
| P-8 (외부발송 in tx) | ✅ notification AFTER_COMMIT 분리 | f75b8b2b |
| P-9 (커밋 전 발행) | ✅ tx 경계 안 발행 → AFTER_COMMIT 라우팅 | (위 포함) |
| 추가 OSIV (message sendMessage) | ✅ @Transactional 제거(UseCase 단일경계) | 6b6187bb |

검증: clean 컴파일 ✅ / DDD 게이트 OSIV 0·고아 0 ✅ / domain+application 단위테스트 0 실패(~583) ✅

## 미해결 / 후속

- **CreatePaymentUseCase** `prepare()` PG-in-tx 잔존 — BE-14b가 명시적으로 out-of-scope 선언한 별도 경로. 후속 티켓.
- **p1~p2 권고** (Stock @Version val→var, CartItemRepositoryImpl 인메모리 필터, Stock 409 핸들러, Product/Stock cascade 이중저장, V35 인덱스 공백) — 미적용, 후속.
- **풀 테스트 스위트**: 로컬 Testcontainers hang(인프라) + 사전존재 시나리오 실패 3건(PaymentWebhookScenarioTest=Kafka autoconfig, PaymentCreateScenarioTest S-03/GoodsSellerApiScenarioTest S-04=검증 status 422/400) → push-test 게이트 차단 요인.

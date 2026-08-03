package com.sportsapp.domain.notification.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * 알림 템플릿 변수 구성 계약.
 *
 * 회귀 방지: 결제 완료 알림 본문이 `88000원 결제가 완료되었습니다.` 로 찍혀, 같은 앱의 장바구니
 * (`총 280,000원`)·티켓 주문 확인(`88,000원`)과 표기가 어긋났다(01-모바일앱/36 캡쳐).
 * 금액을 `Long.toString()` 으로 그대로 실어 천 단위 구분자가 빠진 것이 원인이다.
 */
class NotificationPayloadTest : BehaviorSpec({

    Given("결제 완료 알림 변수를 만들 때") {
        When("금액이 천 단위를 넘으면") {
            Then("천 단위 구분자를 넣어 싣는다") {
                NotificationPayload.paymentCompleted(88_000L).data["amount"] shouldBe "88,000"
            }
        }

        When("금액이 백만 단위면") {
            Then("구분자를 모두 넣는다") {
                NotificationPayload.paymentCompleted(1_234_567L).data["amount"] shouldBe "1,234,567"
            }
        }

        When("금액이 천 미만이면") {
            Then("구분자 없이 그대로 싣는다") {
                NotificationPayload.paymentCompleted(900L).data["amount"] shouldBe "900"
            }
        }

        When("금액이 0이면") {
            Then("0 을 싣는다") {
                NotificationPayload.paymentCompleted(0L).data["amount"] shouldBe "0"
            }
        }

        Then("amount 외의 변수는 싣지 않는다") {
            NotificationPayload.paymentCompleted(1_000L).data.keys shouldBe setOf("amount")
        }
    }

    Given("티켓 발권 알림 변수를 만들 때") {
        Then("경기명을 싣는다") {
            NotificationPayload.ticketIssued("2026 시티리그 4강 홈경기")
                .data["eventTitle"] shouldBe "2026 시티리그 4강 홈경기"
        }
    }

    Given("변수가 없는 알림이면") {
        Then("빈 변수 맵을 만든다") {
            NotificationPayload.empty().data.isEmpty() shouldBe true
        }
    }
})

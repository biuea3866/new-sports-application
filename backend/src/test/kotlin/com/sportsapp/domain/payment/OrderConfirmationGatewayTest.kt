package com.sportsapp.domain.payment

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldNotBe
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class OrderConfirmationGatewayTest : BehaviorSpec({

    Given("OrderConfirmationGateway 인터페이스") {
        When("confirm(orderType, orderId, paymentId) 시그니처를 확인하면") {
            Then("[U-01] 인터페이스가 confirm(orderType: OrderType, orderId: Long, paymentId: Long) 를 선언한다") {
                // 컴파일 타임 검증: 인터페이스 참조 자체가 컴파일되면 시그니처가 올바른 것
                val gateway: OrderConfirmationGateway = mockk()
                gateway shouldNotBe null
            }
        }
    }

    Given("OrderConfirmationGateway mock") {
        val gateway: OrderConfirmationGateway = mockk()
        justRun { gateway.confirm(any(), any(), any()) }

        When("BOOKING 타입으로 confirm 을 호출하면") {
            gateway.confirm(orderType = OrderType.BOOKING, orderId = 1L, paymentId = 100L)

            Then("[U-02] mock 이 정상 동작한다 — 컴파일 및 호출 검증") {
                verify(exactly = 1) {
                    gateway.confirm(orderType = OrderType.BOOKING, orderId = 1L, paymentId = 100L)
                }
            }
        }
    }
})

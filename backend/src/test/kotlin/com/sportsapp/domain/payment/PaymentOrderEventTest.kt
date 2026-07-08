package com.sportsapp.domain.payment

import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.payment.event.PaymentCancelledEvent
import com.sportsapp.domain.payment.event.PaymentCompletedEvent
import com.sportsapp.domain.payment.event.PaymentConfirmedEvent
import com.sportsapp.domain.payment.vo.OrderType
import com.sportsapp.domain.payment.vo.PaymentMethod
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.ZonedDateTime

class PaymentOrderEventTest : BehaviorSpec({

    fun readyPayment(orderType: OrderType, orderId: Long): Payment = Payment.create(
        userId = 1L,
        idempotencyKey = "order-event-key-$orderType-$orderId",
        orderType = orderType,
        orderId = orderId,
        method = PaymentMethod.CREDIT_CARD,
        amount = BigDecimal("10000"),
        currency = "KRW",
    ).also { it.markReady("tid-$orderId", "card", "http://checkout") }

    Given("PaymentConfirmedEvent 인스턴스") {
        val event = PaymentConfirmedEvent(paymentId = 42L, orderType = OrderType.BOOKING, orderId = 300L)

        When("topic 과 필드를 확인하면") {
            Then("전용 확정 토픽과 orderType/orderId/paymentId 를 노출한다") {
                event.topic shouldBe "event.payment.order-confirmed"
                event.aggregateId shouldBe 42L
                event.paymentId shouldBe 42L
                event.orderType shouldBe OrderType.BOOKING
                event.orderId shouldBe 300L
            }
        }
    }

    Given("PaymentCancelledEvent 인스턴스") {
        val event = PaymentCancelledEvent(paymentId = 7L, orderType = OrderType.GOODS, orderId = 900L)

        When("topic 과 필드를 확인하면") {
            Then("전용 취소 토픽과 orderType/orderId/paymentId 를 노출한다") {
                event.topic shouldBe "event.payment.order-cancelled"
                event.aggregateId shouldBe 7L
                event.paymentId shouldBe 7L
                event.orderType shouldBe OrderType.GOODS
                event.orderId shouldBe 900L
            }
        }
    }

    Given("READY 상태 Payment 에서 markCompleted 를 호출하면") {
        val payment = readyPayment(OrderType.TICKETING, 555L)

        When("markCompleted 를 호출하면") {
            payment.markCompleted(ZonedDateTime.now())

            Then("PaymentCompletedEvent 와 PaymentConfirmedEvent 가 함께 적재된다") {
                payment.status shouldBe PaymentStatus.COMPLETED
                val events = payment.pullDomainEvents()
                events.size shouldBe 2
                events.filterIsInstance<PaymentCompletedEvent>().size shouldBe 1
                val confirmed = events.filterIsInstance<PaymentConfirmedEvent>().single()
                confirmed.orderType shouldBe OrderType.TICKETING
                confirmed.orderId shouldBe 555L
                confirmed.paymentId shouldBe payment.id
            }
        }
    }

    Given("READY 상태 Payment 에서 markCancelled 를 호출하면") {
        val payment = readyPayment(OrderType.RECRUITMENT, 777L)

        When("markCancelled 를 호출하면") {
            payment.markCancelled()

            Then("PaymentCancelledEvent 가 적재된다") {
                payment.status shouldBe PaymentStatus.CANCELLED
                val events = payment.pullDomainEvents()
                events.size shouldBe 1
                val cancelled = events.filterIsInstance<PaymentCancelledEvent>().single()
                cancelled.orderType shouldBe OrderType.RECRUITMENT
                cancelled.orderId shouldBe 777L
                cancelled.paymentId shouldBe payment.id
            }
        }
    }

    Given("OrderType 의 사용자 노출 라벨") {
        When("displayName 을 확인하면") {
            Then("기술 식별자가 아닌 한글 도메인 명이 노출된다") {
                OrderType.BOOKING.displayName shouldBe "시설 예약"
                OrderType.TICKETING.displayName shouldBe "티켓 예매"
                OrderType.GOODS.displayName shouldBe "상품 주문"
                OrderType.RECRUITMENT.displayName shouldBe "모집 참가"
            }
        }
    }
})

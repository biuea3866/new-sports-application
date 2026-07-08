package com.sportsapp.domain.payment.event

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.payment.vo.OrderType
import com.sportsapp.domain.payment.vo.PaymentMethod
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal
import java.time.ZonedDateTime

class PaymentEventTest : BehaviorSpec({

    // Kafka Producer/Consumer 가 사용하는 것과 동일한 구성의 ObjectMapper
    val kafkaObjectMapper: ObjectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    fun readyPayment(orderType: OrderType, orderId: Long): Payment = Payment.create(
        userId = 1L,
        idempotencyKey = "payment-event-key-$orderType-$orderId",
        orderType = orderType,
        orderId = orderId,
        method = PaymentMethod.CREDIT_CARD,
        amount = BigDecimal("10000"),
        currency = "KRW",
    ).also { it.markReady("tid-$orderId", "card", "http://checkout") }

    Given("PaymentEvent.Confirmed 인스턴스") {
        val event = PaymentEvent.Confirmed(paymentId = 42L, orderType = OrderType.BOOKING, orderId = 300L)

        When("topic 과 필드를 확인하면") {
            Then("단일 토픽과 orderType/orderId/paymentId 를 노출한다") {
                event.topic shouldBe "event.payment.payment.v1"
                event.aggregateId shouldBe 42L
                event.paymentId shouldBe 42L
                event.orderType shouldBe OrderType.BOOKING
                event.orderId shouldBe 300L
                event.eventType shouldBe "CONFIRMED"
            }
        }
    }

    Given("PaymentEvent.Cancelled 인스턴스") {
        val event = PaymentEvent.Cancelled(paymentId = 7L, orderType = OrderType.GOODS, orderId = 900L)

        When("topic 과 필드를 확인하면") {
            Then("확정 이벤트와 같은 단일 토픽을 쓰고 판별자만 다르다") {
                event.topic shouldBe "event.payment.payment.v1"
                event.aggregateId shouldBe 7L
                event.paymentId shouldBe 7L
                event.orderType shouldBe OrderType.GOODS
                event.orderId shouldBe 900L
                event.eventType shouldBe "CANCELLED"
            }
        }
    }

    Given("PaymentEvent.Confirmed 를 JSON 으로 직렬화한 뒤 sealed 베이스로 역직렬화하면") {
        val original = PaymentEvent.Confirmed(paymentId = 11L, orderType = OrderType.TICKETING, orderId = 111L)
        val json = kafkaObjectMapper.writeValueAsString(original)

        When("eventType 판별자로 하위 타입을 결정하면") {
            val restored = kafkaObjectMapper.readValue(json, PaymentEvent::class.java)

            Then("Confirmed 로 복원되고 필드가 보존된다") {
                restored.shouldBeInstanceOf<PaymentEvent.Confirmed>()
                restored.paymentId shouldBe 11L
                restored.orderType shouldBe OrderType.TICKETING
                restored.orderId shouldBe 111L
                restored.eventId shouldBe original.eventId
            }
        }
    }

    Given("PaymentEvent.Cancelled 를 JSON 으로 직렬화한 뒤 sealed 베이스로 역직렬화하면") {
        val original = PaymentEvent.Cancelled(paymentId = 22L, orderType = OrderType.RECRUITMENT, orderId = 222L)
        val json = kafkaObjectMapper.writeValueAsString(original)

        When("eventType 판별자로 하위 타입을 결정하면") {
            val restored = kafkaObjectMapper.readValue(json, PaymentEvent::class.java)

            Then("Cancelled 로 복원되고 필드가 보존된다") {
                restored.shouldBeInstanceOf<PaymentEvent.Cancelled>()
                restored.paymentId shouldBe 22L
                restored.orderType shouldBe OrderType.RECRUITMENT
                restored.orderId shouldBe 222L
                restored.eventId shouldBe original.eventId
            }
        }
    }

    Given("READY 상태 Payment 에서 markCompleted 를 호출하면") {
        val payment = readyPayment(OrderType.TICKETING, 555L)

        When("markCompleted 를 호출하면") {
            payment.markCompleted(ZonedDateTime.now())

            Then("알림용 PaymentCompletedEvent 와 주문 확정용 PaymentEvent.Confirmed 가 함께 적재된다") {
                payment.status shouldBe PaymentStatus.COMPLETED
                val events = payment.pullDomainEvents()
                events.filterIsInstance<PaymentCompletedEvent>().size shouldBe 1
                val confirmed = events.filterIsInstance<PaymentEvent.Confirmed>().single()
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

            Then("PaymentEvent.Cancelled 가 적재된다") {
                payment.status shouldBe PaymentStatus.CANCELLED
                val events = payment.pullDomainEvents()
                val cancelled = events.filterIsInstance<PaymentEvent.Cancelled>().single()
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

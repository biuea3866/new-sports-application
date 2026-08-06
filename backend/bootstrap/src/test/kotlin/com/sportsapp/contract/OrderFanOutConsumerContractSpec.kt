package com.sportsapp.contract

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.application.booking.dto.InternalBookingOrderHistoryItemResponse
import com.sportsapp.application.goods.dto.InternalGoodsOrderHistoryItemResponse
import com.sportsapp.application.recruitment.dto.InternalRecruitmentApplicationHistoryResponse
import com.sportsapp.application.ticketing.dto.InternalTicketingOrderHistoryItemResponse
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.infrastructure.messaging.KafkaJsonObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * order 읽기 fan-out(C7) 소비자 계약 (S2-10).
 *
 * 2단계에 edge 의 `OrderHistoryRestAdapter` 가 이 공급자 응답을 HTTP 로 받는다. 어댑터는 공급자
 * DTO 타입을 컴파일 의존하지 않으므로 필드가 사라져도 컴파일이 통과한다 — R-20 에서 실제로
 * `amount`·`seats` 가 그렇게 누락됐고, 둘 다 **사용자에게 보이던 결함을 고친 값**이었다.
 *
 * `paymentId`(null 가능)·`status`(문자열)는 FE 가 그대로 렌더링하므로 타입·nullable 을 고정한다.
 * `CrossServiceConsumerContractTest` 는 수정하지 않는다(같은 wave 파일 충돌 방지).
 */
class OrderFanOutConsumerContractSpec : DescribeSpec({

    val objectMapper: ObjectMapper = KafkaJsonObjectMapper().kafkaObjectMapper()
    val fixedAt: ZonedDateTime = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)

    data class Expectation(
        val consumer: String,
        val provider: String,
        val sample: Any,
        val expectedFields: ContractFields,
    )

    /** 네 도메인이 공유하는 최소 형태 — edge 가 orderType·detailPath 를 sourceId 로 파생한다. */
    val sharedFields: ContractFields = mapOf(
        "sourceId" to ContractField(type = "INTEGER", nullable = false),
        "title" to ContractField(type = "STRING", nullable = false),
        "status" to ContractField(type = "STRING", nullable = false),
        // 결제 전 주문은 결제 건이 없다 — nullable 을 필수로 바꾸면 edge 역직렬화가 깨진다.
        "paymentId" to ContractField(type = "INTEGER", nullable = true),
        "createdAt" to ContractField(type = "STRING", nullable = false),
    )

    val expectations = listOf(
        Expectation(
            consumer = "edge OrderHistoryRestAdapter (GoodsOrderHistoryPayload)",
            provider = "commerce InternalGoodsOrderHistoryItemResponse",
            sample = InternalGoodsOrderHistoryItemResponse(
                sourceId = 11L,
                title = "유니폼 외 1건",
                status = GoodsOrderStatus.CONFIRMED,
                paymentId = 5L,
                createdAt = fixedAt,
                amount = BigDecimal("78000.00"),
            ),
            // amount 는 주문 내역 화면의 결제 금액이다 — 누락되면 금액이 사라진다(R-20 유형).
            expectedFields = sharedFields + mapOf("amount" to ContractField(type = "NUMBER", nullable = false)),
        ),
        Expectation(
            consumer = "edge OrderHistoryRestAdapter (TicketingOrderHistoryPayload)",
            provider = "commerce InternalTicketingOrderHistoryItemResponse",
            sample = InternalTicketingOrderHistoryItemResponse(
                sourceId = 21L,
                title = "결승",
                status = OrderStatus.CONFIRMED,
                // 샘플은 **non-null 값**을 넣는다 — ContractShape 는 타입을 실제 직렬화 값에서
                // 뽑으므로 null 을 넣으면 관측 타입이 NULL 이 되어 계약이 아니라 샘플을 검증한다.
                // nullable 여부는 코틀린 프로퍼티 리플렉션에서 따로 온다.
                paymentId = 9L,
                createdAt = fixedAt,
                amount = BigDecimal("160000.00"),
                seats = listOf(
                    InternalTicketingOrderHistoryItemResponse.SeatResponse(section = "A", rowNo = "3", seatNo = "12"),
                ),
            ),
            // seats 는 **원본 필드 배열**이어야 한다 — 문자열로 조합해 보내면 모바일의
            // formatSeatDescription 과 표시 규칙이 두 곳으로 갈린다.
            expectedFields = sharedFields + mapOf(
                "amount" to ContractField(type = "NUMBER", nullable = false),
                "seats" to ContractField(type = "ARRAY", nullable = false),
            ),
        ),
        Expectation(
            consumer = "edge OrderHistoryRestAdapter (BookingOrderHistoryPayload)",
            provider = "facility-booking InternalBookingOrderHistoryItemResponse",
            sample = InternalBookingOrderHistoryItemResponse(
                sourceId = 31L,
                title = "시립수영장 예약",
                status = "CONFIRMED",
                paymentId = 7L,
                createdAt = fixedAt,
                amount = BigDecimal("30000.00"),
            ),
            expectedFields = sharedFields + mapOf("amount" to ContractField(type = "NUMBER", nullable = true)),
        ),
        Expectation(
            consumer = "edge OrderHistoryRestAdapter (RecruitmentOrderHistoryPayload)",
            provider = "social InternalRecruitmentApplicationHistoryResponse",
            sample = InternalRecruitmentApplicationHistoryResponse(
                sourceId = 41L,
                title = "주말 풋살",
                status = ApplicationStatus.CONFIRMED,
                paymentId = 9L,
                createdAt = fixedAt,
                amount = BigDecimal("10000.00"),
            ),
            // 무료 모집은 amount 가 null 이다 — 0 으로 채우면 "0원" 오표시 사고가 재발한다.
            expectedFields = sharedFields + mapOf("amount" to ContractField(type = "NUMBER", nullable = true)),
        ),
    )

    describe("소비자가 기대하는 필드가 공급자 형태에 존재한다") {
        it("기대 선언이 비어 있지 않다 — 공집합 위에서 통과하면 이 스펙은 무력하다") {
            expectations.shouldNotBeEmpty()
        }

        expectations.forEach { expectation ->
            it("${expectation.consumer} → ${expectation.provider}") {
                val providerShape = ContractShape.of(expectation.sample, objectMapper)

                val violations = ContractCompatibility
                    .violationsOf(
                        expectation.expectedFields,
                        providerShape.filterKeys { it in expectation.expectedFields },
                    )
                    .map { "${expectation.consumer} 가 기대하는 ${it.field}: ${it.reason}" }

                violations.shouldBeEmpty()
            }
        }
    }
})

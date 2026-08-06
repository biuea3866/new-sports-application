package com.sportsapp.contract

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.application.goods.dto.InternalGoodsCatalogItemResponse
import com.sportsapp.application.ticketing.dto.InternalTicketingCatalogItemResponse
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.vo.SellerType
import com.sportsapp.domain.ticketing.entity.EventStatus
import com.sportsapp.infrastructure.messaging.KafkaJsonObjectMapper
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * catalog 읽기 fan-out(C6) 소비자 계약 (S2-09).
 *
 * 2단계에 edge 의 `CatalogSearchRestAdapter` 가 이 공급자 응답을 HTTP 로 받는다. 어댑터는 공급자
 * DTO 타입을 컴파일 의존하지 **않으므로**(의존하면 S2-01 의 의존 역전이 무의미해진다) 필드가
 * 사라져도 컴파일이 통과한다 — 실제로 그 방식으로 공급자가 필드를 조용히 누락한 사고가 있었다
 * (등록부 R-20, S2-04·S2-05 에서 발생). 그 드리프트를 머지 전에 잡는 것이 이 스펙의 목적이다.
 *
 * `CrossServiceConsumerContractTest` 를 수정하지 않고 별도 파일로 둔다 — 같은 wave 의 S2-10·S2-12 가
 * 같은 파일을 만지면 머지 충돌이 난다(Single Writer per File).
 *
 * facility(program)·recruitment 공급자 응답은 각각 S2-04·S2-05 가 정의했고 이 스펙이 함께 고정한다.
 */
class CatalogFanOutConsumerContractSpec : DescribeSpec({

    val objectMapper: ObjectMapper = KafkaJsonObjectMapper().kafkaObjectMapper()
    val fixedAt: ZonedDateTime = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)

    data class Expectation(
        val consumer: String,
        val provider: String,
        val sample: Any,
        val expectedFields: ContractFields,
    )

    val expectations = listOf(
        Expectation(
            consumer = "edge CatalogSearchRestAdapter (GoodsCatalogItemPayload)",
            provider = "commerce InternalGoodsCatalogItemResponse",
            sample = InternalGoodsCatalogItemResponse(
                productId = 7L,
                limitedDropId = 42L,
                limitedDropStatus = LimitedDropStatus.SOLD_OUT,
                title = "한정판 유니폼",
                price = BigDecimal("50000.00"),
                sellerType = SellerType.B2C,
                productStatus = ProductStatus.ACTIVE,
                createdAt = fixedAt,
            ),
            // edge 가 itemType·sourceId·detailPath·status 를 이 원자값들로 **직접 파생**한다.
            // limitedDropId 가 있으면 LIMITED_DROP·/limited-drops/{id}·limitedDropStatus 를 쓰고,
            // 없으면 PRODUCT·/products/{productId}·productStatus 를 쓴다. 하나라도 사라지면
            // 품절 한정판이 ACTIVE 로 오노출되거나 상세 경로가 깨진다.
            expectedFields = mapOf(
                "productId" to ContractField(type = "INTEGER", nullable = false),
                "limitedDropId" to ContractField(type = "INTEGER", nullable = true),
                "limitedDropStatus" to ContractField(type = "STRING", nullable = true),
                "title" to ContractField(type = "STRING", nullable = false),
                "price" to ContractField(type = "NUMBER", nullable = false),
                "sellerType" to ContractField(type = "STRING", nullable = true),
                "productStatus" to ContractField(type = "STRING", nullable = false),
                "createdAt" to ContractField(type = "STRING", nullable = false),
            ),
        ),
        Expectation(
            consumer = "edge CatalogSearchRestAdapter (TicketingCatalogItemPayload)",
            provider = "commerce InternalTicketingCatalogItemResponse",
            sample = InternalTicketingCatalogItemResponse(
                sourceId = 9L,
                price = BigDecimal("80000.00"),
                title = "결승",
                status = EventStatus.OPEN,
                createdAt = fixedAt,
                locationName = "서울월드컵경기장",
                scheduledAt = fixedAt,
            ),
            // locationName·scheduledAt 은 **같은 제목의 경기를 구분하는 유일한 근거**다(R-20 에서
            // 실제로 누락됐던 필드 유형). price 는 좌석 미등록 경기에서 null 이 될 수 있다.
            expectedFields = mapOf(
                "sourceId" to ContractField(type = "INTEGER", nullable = false),
                "title" to ContractField(type = "STRING", nullable = false),
                "price" to ContractField(type = "NUMBER", nullable = true),
                "status" to ContractField(type = "STRING", nullable = false),
                "createdAt" to ContractField(type = "STRING", nullable = false),
                "locationName" to ContractField(type = "STRING", nullable = false),
                "scheduledAt" to ContractField(type = "STRING", nullable = false),
            ),
        ),
    )

    describe("소비자가 기대하는 필드가 공급자 형태에 존재한다") {
        it("기대 선언이 비어 있지 않다 — 공집합 위에서 통과하면 이 스펙은 무력하다") {
            expectations.shouldNotBeEmpty()
        }

        expectations.forEach { expectation ->
            it("${expectation.consumer} → ${expectation.provider}") {
                val providerShape = ContractShape.of(expectation.sample, objectMapper)

                // 소비자 기대를 baseline 으로 둔다 — 공급자에 필드가 더 있는 것은 안전(추가는
                // 하위 호환)이고, 기대한 필드가 없거나 형태가 바뀐 것만 위반이다.
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

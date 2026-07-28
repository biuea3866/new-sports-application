package com.sportsapp.infrastructure.community.gateway

import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.repository.SlotRepository
import com.sportsapp.domain.booking.service.SlotDomainService
import com.sportsapp.domain.community.gateway.SlotInfoGateway
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime
import kotlin.reflect.full.primaryConstructor

/**
 * [SlotInfoGatewayImpl] 전환 검증 (PH0-02).
 *
 * 구현체 의존이 booking [SlotRepository](테이블) 가 아니라 [SlotDomainService](공개 행위 계약)로
 * 교체됐는지를 검증한다 — Repository 모킹이 아니라 DomainService 모킹으로 변환·null 계약을 확인한다.
 */
class SlotInfoGatewayImplTest : BehaviorSpec({

    val slotDomainService = mockk<SlotDomainService>()
    val slotInfoGateway: SlotInfoGateway = SlotInfoGatewayImpl(slotDomainService)

    Given("실제 존재하는 slot") {
        val slot = Slot.create(
            facilityId = "FAC-GW-01",
            date = ZonedDateTime.parse("2026-07-01T09:00:00+09:00"),
            timeRange = "09:00-10:00",
            capacity = 8,
            ownerId = 1L,
        )
        every { slotDomainService.findBy(1L) } returns slot

        When("findBy(slotId)를 호출하면") {
            val slotInfo = slotInfoGateway.findBy(1L)

            Then("시설·일시·시간범위·정원이 슬롯 값 그대로 매핑된다") {
                slotInfo.shouldNotBeNull()
                slotInfo.facilityId shouldBe "FAC-GW-01"
                slotInfo.date shouldBe slot.date
                slotInfo.timeRange shouldBe "09:00-10:00"
                slotInfo.capacity shouldBe 8
            }
        }
    }

    Given("존재하지 않는 slotId") {
        every { slotDomainService.findBy(999_999L) } returns null

        When("findBy(slotId)를 호출하면") {
            val slotInfo = slotInfoGateway.findBy(999_999L)

            Then("예외 없이 null을 반환한다 (예외 전파 금지 계약)") {
                slotInfo.shouldBeNull()
            }
        }
    }

    Given("SlotInfoGatewayImpl 클래스") {
        When("생성자 의존 타입을 확인하면") {
            val constructorParameterTypes = SlotInfoGatewayImpl::class.primaryConstructor
                ?.parameters
                ?.map { it.type.classifier }
                .orEmpty()

            Then("SlotRepository 타입 의존이 남아 있지 않다") {
                constructorParameterTypes.contains(SlotRepository::class).shouldBeFalse()
            }
        }
    }
})

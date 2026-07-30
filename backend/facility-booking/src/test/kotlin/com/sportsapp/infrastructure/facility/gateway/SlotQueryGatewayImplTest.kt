package com.sportsapp.infrastructure.facility.gateway

import com.sportsapp.domain.booking.repository.SlotRepository
import com.sportsapp.domain.booking.service.SlotDomainService
import com.sportsapp.domain.facility.gateway.SlotQueryGateway
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlin.reflect.full.primaryConstructor

/**
 * [SlotQueryGatewayImpl] 공급자 DomainService 경유 전환 검증.
 *
 * 구현체 의존이 booking [SlotRepository](테이블) 가 아니라 [SlotDomainService](공개 행위 계약)로
 * 교체됐는지를 검증한다.
 */
class SlotQueryGatewayImplTest : BehaviorSpec({

    val slotDomainService = mockk<SlotDomainService>()
    val slotQueryGateway: SlotQueryGateway = SlotQueryGatewayImpl(slotDomainService)

    Given("시설에 활성 슬롯이 존재할 때") {
        every { slotDomainService.hasActiveSlots("FAC-ACTIVE") } returns true

        When("hasActiveSlots를 호출하면") {
            val result = slotQueryGateway.hasActiveSlots("FAC-ACTIVE")

            Then("true를 반환한다") {
                result shouldBe true
            }
        }
    }

    Given("시설에 활성 슬롯이 하나도 없을 때") {
        every { slotDomainService.hasActiveSlots("FAC-EMPTY") } returns false

        When("hasActiveSlots를 호출하면") {
            val result = slotQueryGateway.hasActiveSlots("FAC-EMPTY")

            Then("false를 반환한다") {
                result shouldBe false
            }
        }
    }

    Given("SlotQueryGatewayImpl 클래스") {
        When("생성자 의존 타입을 확인하면") {
            val constructorParameterTypes = SlotQueryGatewayImpl::class.primaryConstructor
                ?.parameters
                ?.map { it.type.classifier }
                .orEmpty()

            Then("SlotRepository 타입 의존이 남아 있지 않다") {
                constructorParameterTypes.contains(SlotRepository::class).shouldBeFalse()
            }
        }
    }
})

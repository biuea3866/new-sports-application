package com.sportsapp.application.facility

import com.sportsapp.domain.booking.service.SlotDomainService
import com.sportsapp.domain.facility.FacilityHasActiveSlotException
import com.sportsapp.domain.facility.FacilityNotFoundException
import com.sportsapp.domain.facility.FacilityOwnerDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk

class DeleteMyFacilityUseCaseTest : BehaviorSpec({

    val slotDomainService = mockk<SlotDomainService>()
    val facilityOwnerDomainService = mockk<FacilityOwnerDomainService>()
    val useCase = DeleteMyFacilityUseCase(slotDomainService, facilityOwnerDomainService)

    Given("ownerUserId=1L 소유 시설에 활성 슬롯이 없을 때") {
        val command = DeleteMyFacilityCommand(facilityId = "f-001", ownerUserId = 1L)
        every { slotDomainService.hasActiveSlotsByFacilityId("f-001") } returns false
        justRun { facilityOwnerDomainService.deleteForOwner("f-001", 1L) }

        When("[U-01] execute를 호출하면") {
            Then("예외 없이 정상 완료된다") {
                useCase.execute(command)
            }
        }
    }

    Given("활성 슬롯이 있는 시설 삭제 command가 주어졌을 때") {
        val command = DeleteMyFacilityCommand(facilityId = "f-002", ownerUserId = 1L)
        every { slotDomainService.hasActiveSlotsByFacilityId("f-002") } returns true

        When("[U-02] execute를 호출하면") {
            Then("FacilityHasActiveSlotException을 던진다") {
                shouldThrow<FacilityHasActiveSlotException> {
                    useCase.execute(command)
                }
            }
        }
    }

    Given("다른 사용자 소유 시설 ID로 command가 주어졌을 때") {
        val command = DeleteMyFacilityCommand(facilityId = "f-999", ownerUserId = 1L)
        every { slotDomainService.hasActiveSlotsByFacilityId("f-999") } returns false
        every { facilityOwnerDomainService.deleteForOwner("f-999", 1L) } throws FacilityNotFoundException("f-999")

        When("[U-03] execute를 호출하면") {
            Then("FacilityNotFoundException을 던진다") {
                shouldThrow<FacilityNotFoundException> {
                    useCase.execute(command)
                }
            }
        }
    }
})

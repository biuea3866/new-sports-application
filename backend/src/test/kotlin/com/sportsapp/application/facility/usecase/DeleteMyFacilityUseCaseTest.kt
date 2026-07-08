package com.sportsapp.application.facility.usecase

import com.sportsapp.application.facility.dto.DeleteMyFacilityCommand
import com.sportsapp.domain.facility.exception.FacilityHasActiveSlotException
import com.sportsapp.domain.facility.exception.FacilityNotFoundException
import com.sportsapp.domain.facility.service.FacilityOwnerDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class DeleteMyFacilityUseCaseTest : BehaviorSpec({

    val facilityOwnerDomainService = mockk<FacilityOwnerDomainService>()
    val useCase = DeleteMyFacilityUseCase(facilityOwnerDomainService)

    Given("삭제 가능한 소유 시설 command가 주어졌을 때") {
        val command = DeleteMyFacilityCommand(facilityId = "f-001", ownerUserId = 1L)
        justRun { facilityOwnerDomainService.deleteForOwner("f-001", 1L) }

        When("execute를 호출하면") {
            useCase.execute(command)

            Then("도메인 서비스에 삭제를 위임한다") {
                verify(exactly = 1) { facilityOwnerDomainService.deleteForOwner("f-001", 1L) }
            }
        }
    }

    Given("활성 슬롯이 있는 시설 삭제 command가 주어졌을 때") {
        val command = DeleteMyFacilityCommand(facilityId = "f-002", ownerUserId = 1L)
        every { facilityOwnerDomainService.deleteForOwner("f-002", 1L) } throws FacilityHasActiveSlotException("f-002")

        When("execute를 호출하면") {
            Then("FacilityHasActiveSlotException이 전파된다") {
                shouldThrow<FacilityHasActiveSlotException> {
                    useCase.execute(command)
                }
            }
        }
    }

    Given("다른 사용자 소유 시설 ID로 command가 주어졌을 때") {
        val command = DeleteMyFacilityCommand(facilityId = "f-999", ownerUserId = 1L)
        every { facilityOwnerDomainService.deleteForOwner("f-999", 1L) } throws FacilityNotFoundException("f-999")

        When("execute를 호출하면") {
            Then("FacilityNotFoundException이 전파된다") {
                shouldThrow<FacilityNotFoundException> {
                    useCase.execute(command)
                }
            }
        }
    }
})

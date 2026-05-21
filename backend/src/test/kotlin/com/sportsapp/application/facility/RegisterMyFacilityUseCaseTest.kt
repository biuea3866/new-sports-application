package com.sportsapp.application.facility

import com.sportsapp.domain.booking.SlotDomainService
import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.facility.Facility
import com.sportsapp.domain.facility.FacilityDomainService
import com.sportsapp.domain.facility.FacilityHasActiveSlotException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.geo.Point

class RegisterMyFacilityUseCaseTest : BehaviorSpec({

    val facilityDomainService = mockk<FacilityDomainService>()
    val slotDomainService = mockk<SlotDomainService>()
    val ownershipGuard = mockk<OwnershipGuard>()
    val registerUseCase = RegisterMyFacilityUseCase(facilityDomainService, ownershipGuard)
    val deleteUseCase = DeleteMyFacilityUseCase(facilityDomainService, slotDomainService, ownershipGuard)
    val updateUseCase = UpdateMyFacilityUseCase(facilityDomainService, ownershipGuard)

    fun buildFacility(id: String = "fac-001", ownerUserId: Long? = 1L): Facility =
        Facility(
            id = id,
            code = "GN-001",
            name = "테스트 시설",
            gu = "강남구",
            type = "수영장",
            address = "서울시 강남구",
            location = Point(127.0, 37.5),
            parking = true,
            tel = "02-0000-0000",
            homePage = "",
            eduYn = false,
            meta = emptyMap(),
            ownerUserId = ownerUserId,
        )

    Given("[U-01] RegisterMyFacilityUseCase — authUserId로 registerForOwner 호출") {
        val authUserId = 1L
        val command = RegisterMyFacilityCommand(
            code = "GN-001",
            name = "테스트 시설",
            gu = "강남구",
            type = "수영장",
            address = "서울시 강남구",
            lat = 37.5,
            lng = 127.0,
            parking = true,
            tel = "02-0000-0000",
            homePage = "",
            eduYn = false,
            meta = emptyMap(),
            authUserId = authUserId,
        )

        every { facilityDomainService.registerForOwner(any(), authUserId) } returns buildFacility(ownerUserId = authUserId)

        When("execute 호출 시") {
            val result = registerUseCase.execute(command)

            Then("[U-01] FacilityDomainService.registerForOwner 가 authUserId 와 함께 호출된다") {
                verify(exactly = 1) { facilityDomainService.registerForOwner(any(), authUserId) }
                result.ownerUserId shouldBe authUserId
            }
        }
    }

    Given("[U-02] DeleteMyFacilityUseCase — 활성 슬롯 존재 시 FacilityHasActiveSlotException 발생") {
        val authUserId = 1L
        val facilityId = "fac-001"

        every { slotDomainService.existsActiveByFacilityId(facilityId) } returns true

        When("활성 슬롯이 있는 시설을 삭제 요청하면") {
            Then("[U-02] FacilityHasActiveSlotException이 발생한다") {
                shouldThrow<FacilityHasActiveSlotException> {
                    deleteUseCase.execute(DeleteMyFacilityCommand(facilityId = facilityId, authUserId = authUserId))
                }
            }
        }
    }

    Given("[U-03] UpdateMyFacilityUseCase — FacilityDomainService.updateMetaForOwner 위임") {
        val authUserId = 1L
        val facilityId = "fac-001"
        val facility = buildFacility(id = facilityId, ownerUserId = authUserId)

        every { facilityDomainService.updateMetaForOwner(facilityId, authUserId, any()) } returns facility

        When("수정 요청 시") {
            updateUseCase.execute(
                UpdateMyFacilityCommand(
                    facilityId = facilityId,
                    patch = mapOf("key" to "value"),
                    authUserId = authUserId,
                ),
            )

            Then("[U-03] FacilityDomainService.updateMetaForOwner 가 호출된다") {
                verify(exactly = 1) { facilityDomainService.updateMetaForOwner(facilityId, authUserId, any()) }
            }
        }
    }
})

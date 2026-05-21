package com.sportsapp.application.facility

import com.sportsapp.domain.facility.Facility
import com.sportsapp.domain.facility.FacilityDomainService
import com.sportsapp.domain.facility.FacilityNotOwnedByException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.geo.Point

private fun buildFacility(id: String = "fac-001", ownerUserId: Long? = 1L): Facility = Facility(
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

class UpdateFacilityUseCaseTest : BehaviorSpec({

    val facilityDomainService = mockk<FacilityDomainService>()
    val updateFacilityUseCase = UpdateFacilityUseCase(facilityDomainService)

    Given("[U-02] 운영자 본인의 시설 수정") {
        val command = UpdateFacilityCommand(
            operatorId = 1L,
            facilityId = "fac-001",
            name = "수정된 시설명",
        )
        val updatedFacility = buildFacility(ownerUserId = 1L).let {
            Facility(
                id = it.id,
                code = it.code,
                name = "수정된 시설명",
                gu = it.gu,
                type = it.type,
                address = it.address,
                location = it.location,
                parking = it.parking,
                tel = it.tel,
                homePage = it.homePage,
                eduYn = it.eduYn,
                meta = it.meta,
                ownerUserId = it.ownerUserId,
            )
        }
        every { facilityDomainService.update(1L, "fac-001", any()) } returns updatedFacility

        When("[U-02] execute 호출 시") {
            val result = updateFacilityUseCase.execute(command)

            Then("[U-02] DomainService.update가 호출되고 FacilityResponse를 반환한다") {
                verify(exactly = 1) { facilityDomainService.update(1L, "fac-001", any()) }
                result.name shouldBe "수정된 시설명"
            }
        }
    }

    Given("[U-02] 타인 시설 수정 시도") {
        val command = UpdateFacilityCommand(
            operatorId = 2L,
            facilityId = "fac-001",
            name = "수정시도",
        )
        every { facilityDomainService.update(2L, "fac-001", any()) } throws FacilityNotOwnedByException("fac-001", 2L)

        When("[U-02] execute 호출 시") {
            Then("[U-02] FacilityNotOwnedByException이 발생한다") {
                shouldThrow<FacilityNotOwnedByException> {
                    updateFacilityUseCase.execute(command)
                }
            }
        }
    }
})

package com.sportsapp.application.facility

import com.sportsapp.domain.facility.Facility
import com.sportsapp.domain.facility.FacilityNotFoundException
import com.sportsapp.domain.facility.FacilityOwnerDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.geo.Point

class UpdateMyFacilityUseCaseTest : BehaviorSpec({

    val facilityOwnerDomainService = mockk<FacilityOwnerDomainService>()
    val useCase = UpdateMyFacilityUseCase(facilityOwnerDomainService)

    Given("ownerUserId=1L 소유 시설에 meta 패치 command가 주어졌을 때") {
        val command = UpdateMyFacilityCommand(
            facilityId = "f-001",
            ownerUserId = 1L,
            patch = mapOf("key" to "value"),
        )
        val updatedFacility = Facility(
            id = "f-001", code = "C-001", name = "강남 수영장",
            gu = "강남구", type = "수영장", address = "서울시 강남구",
            location = Point(127.0, 37.5),
            parking = true, tel = "02-0000-0000", homePage = "", eduYn = false,
            meta = mapOf("key" to "value"), ownerUserId = 1L,
        )
        every { facilityOwnerDomainService.updateMetaForOwner("f-001", 1L, mapOf("key" to "value")) } returns updatedFacility

        When("[U-01] execute를 호출하면") {
            val result = useCase.execute(command)

            Then("업데이트된 FacilityResponse가 반환된다") {
                result.id shouldBe "f-001"
            }
        }
    }

    Given("다른 사용자 소유 시설 ID로 command가 주어졌을 때") {
        val command = UpdateMyFacilityCommand(
            facilityId = "f-999",
            ownerUserId = 1L,
            patch = mapOf("key" to "value"),
        )
        every { facilityOwnerDomainService.updateMetaForOwner("f-999", 1L, any()) } throws FacilityNotFoundException("f-999")

        When("[U-02] execute를 호출하면") {
            Then("FacilityNotFoundException을 던진다") {
                shouldThrow<FacilityNotFoundException> {
                    useCase.execute(command)
                }
            }
        }
    }
})

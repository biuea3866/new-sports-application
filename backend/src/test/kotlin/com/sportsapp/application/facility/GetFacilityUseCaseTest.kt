package com.sportsapp.application.facility

import com.sportsapp.domain.facility.Facility
import com.sportsapp.domain.facility.FacilityDomainService
import com.sportsapp.domain.facility.FacilityNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.geo.Point

private fun buildFacility(id: String, gu: String, type: String): Facility = Facility(
    id = id,
    code = "CODE-$id",
    name = "시설 $id",
    gu = gu,
    type = type,
    address = "서울시 $gu",
    location = Point(127.0, 37.5),
    parking = true,
    tel = "02-0000-0000",
    homePage = "",
    eduYn = false,
    meta = emptyMap(),
)

class GetFacilityUseCaseTest : BehaviorSpec({

    val facilityDomainService = mockk<FacilityDomainService>()
    val getFacilityUseCase = GetFacilityUseCase(facilityDomainService)

    Given("존재하지 않는 시설 ID가 주어졌을 때") {
        every { facilityDomainService.getById("nonexistent-id") } throws FacilityNotFoundException("nonexistent-id")

        When("[U-01] execute를 호출하면") {
            Then("FacilityNotFoundException을 던진다") {
                shouldThrow<FacilityNotFoundException> {
                    getFacilityUseCase.execute("nonexistent-id")
                }
            }
        }
    }

    Given("존재하는 시설 ID가 주어졌을 때") {
        val facility = buildFacility("existing-id", "강남구", "수영장")
        every { facilityDomainService.getById("existing-id") } returns facility

        When("execute를 호출하면") {
            val result = getFacilityUseCase.execute("existing-id")

            Then("FacilityResponse가 반환된다") {
                result.id shouldBe "existing-id"
                result.gu shouldBe "강남구"
                result.type shouldBe "수영장"
            }
        }
    }
})

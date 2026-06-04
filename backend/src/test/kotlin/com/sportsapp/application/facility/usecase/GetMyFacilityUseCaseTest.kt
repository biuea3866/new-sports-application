package com.sportsapp.application.facility.usecase

import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.exception.FacilityNotFoundException
import com.sportsapp.domain.facility.service.FacilityOwnerDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.geo.Point

class GetMyFacilityUseCaseTest : BehaviorSpec({

    val facilityOwnerDomainService = mockk<FacilityOwnerDomainService>()
    val useCase = GetMyFacilityUseCase(facilityOwnerDomainService)

    Given("ownerUserId=1L 소유의 시설이 존재할 때") {
        val facility = Facility(
            id = "f-001", code = "C-001", name = "강남 수영장",
            gu = "강남구", type = "수영장", address = "서울시 강남구",
            location = Point(127.0, 37.5),
            parking = true, tel = "02-0000-0000", homePage = "", eduYn = false,
            meta = emptyMap(), ownerUserId = 1L,
        )
        every { facilityOwnerDomainService.getByIdAndOwner("f-001", 1L) } returns facility

        When("[U-01] 본인 소유 시설 ID로 execute를 호출하면") {
            val result = useCase.execute("f-001", 1L)

            Then("FacilityResponse가 반환된다") {
                result.id shouldBe "f-001"
                result.name shouldBe "강남 수영장"
            }
        }
    }

    Given("다른 사용자 소유 또는 존재하지 않는 시설 ID가 주어졌을 때") {
        every { facilityOwnerDomainService.getByIdAndOwner("f-999", 1L) } throws FacilityNotFoundException("f-999")

        When("[U-02] execute를 호출하면") {
            Then("FacilityNotFoundException을 던진다") {
                shouldThrow<FacilityNotFoundException> {
                    useCase.execute("f-999", 1L)
                }
            }
        }
    }
})

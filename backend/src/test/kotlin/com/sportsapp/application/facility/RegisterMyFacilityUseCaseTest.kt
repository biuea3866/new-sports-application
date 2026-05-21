package com.sportsapp.application.facility

import com.sportsapp.domain.facility.Facility
import com.sportsapp.domain.facility.FacilityAttributes
import com.sportsapp.domain.facility.FacilityOwnerDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.geo.Point

class RegisterMyFacilityUseCaseTest : BehaviorSpec({

    val facilityOwnerDomainService = mockk<FacilityOwnerDomainService>()
    val useCase = RegisterMyFacilityUseCase(facilityOwnerDomainService)

    Given("유효한 RegisterMyFacilityCommand가 주어졌을 때") {
        val command = RegisterMyFacilityCommand(
            code = "GN-SW-001",
            name = "강남 수영장",
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
            ownerUserId = 1L,
        )
        val savedFacility = Facility(
            id = "facility-001",
            code = command.code,
            name = command.name,
            gu = command.gu,
            type = command.type,
            address = command.address,
            location = Point(command.lng, command.lat),
            parking = command.parking,
            tel = command.tel,
            homePage = command.homePage,
            eduYn = command.eduYn,
            meta = command.meta,
            ownerUserId = command.ownerUserId,
        )
        every { facilityOwnerDomainService.registerForOwner(any<FacilityAttributes>(), command.ownerUserId) } returns savedFacility

        When("[U-01] execute를 호출하면") {
            val result = useCase.execute(command)

            Then("FacilityResponse가 반환된다") {
                result.id shouldBe "facility-001"
                result.name shouldBe "강남 수영장"
                result.gu shouldBe "강남구"
            }
        }
    }
})

package com.sportsapp.application.facility.usecase

import com.sportsapp.application.facility.dto.RemoveHolidayCommand
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.service.FacilityOwnerDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.geo.Point
import java.time.LocalDate

class RemoveHolidayUseCaseTest : BehaviorSpec({

    val facilityOwnerDomainService = mockk<FacilityOwnerDomainService>()
    val useCase = RemoveHolidayUseCase(facilityOwnerDomainService)

    Given("소유 시설에 휴무일 제거 command가 주어졌을 때") {
        val date = LocalDate.of(2026, 7, 6)
        val command = RemoveHolidayCommand(facilityId = "f-001", ownerUserId = 1L, date = date)
        val updatedFacility = Facility(
            id = "f-001", code = "C-001", name = "강남 수영장",
            gu = "강남구", type = "수영장", address = "서울시 강남구",
            location = Point(127.0, 37.5),
            parking = true, tel = "02-0000-0000", homePage = "", eduYn = false,
            meta = emptyMap(), ownerUserId = 1L,
            sidoCode = null, sidoName = null, sigunguCode = null, sigunguName = null,
        )
        every { facilityOwnerDomainService.removeHoliday("f-001", 1L, date) } returns updatedFacility

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("휴무일이 제거된 Facility가 반환된다") {
                result.isHoliday(date) shouldBe false
            }
        }
    }
})

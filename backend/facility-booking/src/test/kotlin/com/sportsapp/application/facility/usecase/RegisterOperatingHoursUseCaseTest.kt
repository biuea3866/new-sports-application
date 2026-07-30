package com.sportsapp.application.facility.usecase

import com.sportsapp.application.facility.dto.RegisterOperatingHoursCommand
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.exception.UnauthorizedFacilityAccessException
import com.sportsapp.domain.facility.service.FacilityOwnerDomainService
import com.sportsapp.domain.facility.vo.OperatingHours
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.geo.Point
import java.time.DayOfWeek
import java.time.LocalTime

class RegisterOperatingHoursUseCaseTest : BehaviorSpec({

    val facilityOwnerDomainService = mockk<FacilityOwnerDomainService>()
    val useCase = RegisterOperatingHoursUseCase(facilityOwnerDomainService)

    Given("소유 시설에 운영시간 등록 command가 주어졌을 때") {
        val hours = listOf(
            OperatingHours(
                dayOfWeek = DayOfWeek.MONDAY,
                openTime = LocalTime.of(6, 0),
                closeTime = LocalTime.of(22, 0),
                capacity = 10,
            ),
        )
        val command = RegisterOperatingHoursCommand(facilityId = "f-001", ownerUserId = 1L, operatingHours = hours)
        val updatedFacility = Facility(
            id = "f-001", code = "C-001", name = "강남 수영장",
            gu = "강남구", type = "수영장", address = "서울시 강남구",
            location = Point(127.0, 37.5),
            parking = true, tel = "02-0000-0000", homePage = "", eduYn = false,
            meta = emptyMap(), ownerUserId = 1L,
            sidoCode = null, sidoName = null, sigunguCode = null, sigunguName = null,
            operatingHours = hours,
        )
        every { facilityOwnerDomainService.registerOperatingHours("f-001", 1L, hours) } returns updatedFacility

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("DomainService가 반환한 Facility가 그대로 반환된다") {
                result.operatingHours shouldHaveSize 1
            }
        }
    }

    Given("소유하지 않은 시설에 운영시간 등록 command가 주어졌을 때") {
        val command = RegisterOperatingHoursCommand(facilityId = "f-002", ownerUserId = 99L, operatingHours = emptyList())
        every {
            facilityOwnerDomainService.registerOperatingHours("f-002", 99L, emptyList())
        } throws UnauthorizedFacilityAccessException("f-002")

        When("execute를 호출하면") {
            Then("UnauthorizedFacilityAccessException을 던진다") {
                shouldThrow<UnauthorizedFacilityAccessException> {
                    useCase.execute(command)
                }
            }
        }
    }
})

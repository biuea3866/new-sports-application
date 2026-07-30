package com.sportsapp.presentation.facility.dto.response

import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.vo.OperatingHours
import com.sportsapp.domain.facility.vo.TimeRange
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.data.geo.Point
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class FacilityResponseTest : BehaviorSpec({

    fun buildFacility(): Facility = Facility(
        id = "f-001", code = "C-001", name = "강남 수영장",
        gu = "강남구", type = "수영장", address = "서울시 강남구",
        location = Point(127.0, 37.5),
        parking = true, tel = "02-0000-0000", homePage = "", eduYn = false,
        meta = emptyMap(), ownerUserId = 1L,
        sidoCode = null, sidoName = null, sigunguCode = null, sigunguName = null,
    )

    Given("운영시간·휴무일이 등록되지 않은 Facility") {
        val facility = buildFacility()

        When("FacilityResponse.of를 호출하면") {
            val response = FacilityResponse.of(facility)

            Then("operatingHours·holidays가 빈 배열로 정상 반환된다(에러 아님)") {
                response.operatingHours.shouldBeEmpty()
                response.holidays.shouldBeEmpty()
            }
        }
    }

    Given("운영시간·휴무일이 등록된 Facility") {
        val facility = buildFacility()
        val hours = OperatingHours(
            dayOfWeek = DayOfWeek.MONDAY,
            openTime = LocalTime.of(6, 0),
            closeTime = LocalTime.of(22, 0),
            breaks = listOf(TimeRange(start = LocalTime.of(12, 0), end = LocalTime.of(13, 0))),
            capacity = 10,
        )
        facility.registerOperatingHours(listOf(hours))
        val holidayDate = LocalDate.of(2026, 7, 6)
        facility.addHoliday(holidayDate)

        When("FacilityResponse.of를 호출하면") {
            val response = FacilityResponse.of(facility)

            Then("등록된 operatingHours가 응답에 포함된다") {
                response.operatingHours shouldHaveSize 1
                response.operatingHours[0].dayOfWeek shouldBe "MONDAY"
                response.operatingHours[0].openTime shouldBe "06:00"
                response.operatingHours[0].closeTime shouldBe "22:00"
                response.operatingHours[0].breaks shouldHaveSize 1
            }

            Then("등록된 holidays가 응답에 포함된다") {
                response.holidays shouldHaveSize 1
                response.holidays[0] shouldBe holidayDate.toString()
            }
        }
    }
})

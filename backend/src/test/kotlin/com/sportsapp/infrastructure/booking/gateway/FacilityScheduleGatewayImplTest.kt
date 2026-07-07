package com.sportsapp.infrastructure.booking.gateway

import com.sportsapp.BaseMongoIntegrationTest
import com.sportsapp.domain.booking.gateway.FacilityScheduleGateway
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.vo.FacilityRegion
import com.sportsapp.domain.facility.vo.Holiday
import com.sportsapp.domain.facility.vo.OperatingHours
import com.sportsapp.domain.facility.vo.TimeRange
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.geo.Point
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query

class FacilityScheduleGatewayImplTest(
    @Autowired private val facilityScheduleGateway: FacilityScheduleGateway,
    @Autowired private val mongoTemplate: MongoTemplate,
) : BaseMongoIntegrationTest() {

    private fun baseFacility(
        id: String,
        ownerUserId: Long?,
        operatingHours: List<OperatingHours> = emptyList(),
        holidays: List<Holiday> = emptyList(),
    ): Facility = Facility(
        id = id,
        code = "CODE-$id",
        name = "시설 $id",
        gu = "강남구",
        type = "풋살장",
        address = "서울시 강남구",
        location = Point(127.0, 37.5),
        parking = true,
        tel = "02-0000-0000",
        homePage = "",
        eduYn = false,
        meta = emptyMap(),
        ownerUserId = ownerUserId,
        sidoCode = FacilityRegion.UNSPECIFIED.sidoCode,
        sidoName = FacilityRegion.UNSPECIFIED.sidoName,
        sigunguCode = FacilityRegion.UNSPECIFIED.sigunguCode,
        sigunguName = FacilityRegion.UNSPECIFIED.sigunguName,
        operatingHours = operatingHours,
        holidays = holidays,
    )

    init {
        Given("운영시간·휴무가 등록된 소유자 있는 시설과, 대상이 아닌 시설들이 섞여 있을 때") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val schedulableHours = OperatingHours(
                dayOfWeek = DayOfWeek.MONDAY,
                openTime = LocalTime.of(9, 0),
                closeTime = LocalTime.of(12, 0),
                breaks = listOf(TimeRange(LocalTime.of(10, 0), LocalTime.of(10, 30))),
                slotDurationMinutes = 60,
                capacity = 4,
            )
            val holidayDate = LocalDate.of(2026, 8, 1)
            mongoTemplate.save(
                baseFacility(
                    id = "FAC-SCHEDULABLE",
                    ownerUserId = 10L,
                    operatingHours = listOf(schedulableHours),
                    holidays = listOf(Holiday(holidayDate)),
                ),
            )
            mongoTemplate.save(baseFacility(id = "FAC-NO-HOURS", ownerUserId = 20L))
            mongoTemplate.save(
                baseFacility(
                    id = "FAC-NO-OWNER",
                    ownerUserId = null,
                    operatingHours = listOf(schedulableHours),
                ),
            )

            When("findSchedulableFacilities를 호출하면") {
                val schedules = facilityScheduleGateway.findSchedulableFacilities()

                Then("운영시간이 등록되고 소유자가 있는 시설만 반환된다") {
                    schedules.map { it.facilityId } shouldBe listOf("FAC-SCHEDULABLE")
                }

                Then("휴무일이 booking DTO의 holidays로 변환된다") {
                    schedules.first().holidays shouldBe setOf(holidayDate)
                }

                Then("브레이크타임이 제외된 timeRange 목록으로 변환된다") {
                    val weeklyHours = schedules.first().weeklyHours
                    weeklyHours shouldHaveSize 1
                    weeklyHours.first().dayOfWeek shouldBe DayOfWeek.MONDAY
                    weeklyHours.first().timeRanges shouldContainExactlyInAnyOrder listOf("09:00-10:00", "11:00-12:00")
                    weeklyHours.first().capacity shouldBe 4
                }
            }
        }

        Given("운영시간·소유자가 모두 없는 시설만 존재할 때") {
            mongoTemplate.remove(Query(), Facility::class.java)
            mongoTemplate.save(baseFacility(id = "FAC-EMPTY", ownerUserId = null))

            When("findSchedulableFacilities를 호출하면") {
                val schedules = facilityScheduleGateway.findSchedulableFacilities()

                Then("빈 목록을 반환한다") {
                    schedules shouldHaveSize 0
                }
            }
        }
    }
}

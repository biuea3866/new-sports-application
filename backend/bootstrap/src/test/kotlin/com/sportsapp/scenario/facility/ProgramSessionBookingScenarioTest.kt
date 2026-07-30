package com.sportsapp.scenario.facility

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.facility.dto.CreateProgramSessionCommand
import com.sportsapp.application.facility.dto.RegisterProgramCommand
import com.sportsapp.application.facility.usecase.CreateProgramSessionUseCase
import com.sportsapp.application.facility.usecase.RegisterProgramUseCase
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.exception.SlotFullException
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.vo.FacilityRegion
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.geo.Point
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.jdbc.core.JdbcTemplate

/**
 * BE-59 "program 회차 예약은 기존 booking 경로로 정상 결제된다" / "정원이 찬 program 회차 예약은
 * SlotFullException으로 거부된다" — booking 도메인 파일은 일절 수정하지 않고(Single Writer),
 * [BookingDomainService.requestBooking]을 그대로 재사용해 검증한다.
 */
class ProgramSessionBookingScenarioTest(
    @Autowired private val registerProgramUseCase: RegisterProgramUseCase,
    @Autowired private val createProgramSessionUseCase: CreateProgramSessionUseCase,
    @Autowired private val bookingDomainService: BookingDomainService,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val mongoTemplate: MongoTemplate,
) : BaseIntegrationTest() {

    private fun seedFacility(id: String, ownerUserId: Long) {
        mongoTemplate.save(
            Facility(
                id = id,
                code = "CODE-$id",
                name = "시설 $id",
                gu = "강남구",
                type = "체육관",
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
            ),
        )
    }

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE bookings")
            jdbcTemplate.execute("TRUNCATE TABLE slots")
            jdbcTemplate.execute("TRUNCATE TABLE programs")
            mongoTemplate.remove(Query(), Facility::class.java)
        }

        Given("소유자가 정원 5명 PT program 을 등록하고 회차를 생성했을 때") {
            seedFacility(id = "FAC-PROGRAM-BOOKING-01", ownerUserId = 1L)
            val program = registerProgramUseCase.execute(
                RegisterProgramCommand(
                    facilityId = "FAC-PROGRAM-BOOKING-01",
                    ownerUserId = 1L,
                    name = "그룹 클래스",
                    description = null,
                    price = BigDecimal("30000"),
                    capacity = 5,
                    durationMinutes = 50,
                ),
            )
            val session = createProgramSessionUseCase.execute(
                CreateProgramSessionCommand(
                    requesterId = 1L,
                    programId = program.id,
                    date = ZonedDateTime.now(),
                    timeRange = "09:00-10:00",
                ),
            )

            When("기존 booking 경로로 예약을 요청하면") {
                val result = bookingDomainService.requestBooking(userId = 10L, slotId = session.slotId)

                Then("PENDING 상태로 정상 예약된다") {
                    result.status shouldBe BookingStatus.PENDING
                    result.slotId shouldBe session.slotId
                }
            }
        }

        Given("정원 1명 program 회차가 이미 예약으로 가득 찼을 때") {
            seedFacility(id = "FAC-PROGRAM-BOOKING-02", ownerUserId = 1L)
            val program = registerProgramUseCase.execute(
                RegisterProgramCommand(
                    facilityId = "FAC-PROGRAM-BOOKING-02",
                    ownerUserId = 1L,
                    name = "1:1 PT",
                    description = null,
                    price = BigDecimal("50000"),
                    capacity = 1,
                    durationMinutes = 60,
                ),
            )
            val session = createProgramSessionUseCase.execute(
                CreateProgramSessionCommand(
                    requesterId = 1L,
                    programId = program.id,
                    date = ZonedDateTime.now(),
                    timeRange = "10:00-11:00",
                ),
            )
            bookingDomainService.requestBooking(userId = 10L, slotId = session.slotId)

            When("추가로 예약을 요청하면") {
                Then("SlotFullException 이 발생한다") {
                    shouldThrow<SlotFullException> {
                        bookingDomainService.requestBooking(userId = 11L, slotId = session.slotId)
                    }
                }
            }
        }
    }
}

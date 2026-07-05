package com.sportsapp.scenario.booking

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.repository.SlotRepository
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.vo.FacilityRegion
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.data.geo.Point
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime

class SlotApiScenarioTest(
    @Autowired private val slotRepository: SlotRepository,
    @Autowired private val bookingDomainService: BookingDomainService,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val mongoTemplate: MongoTemplate,
    @Autowired private val restTemplate: TestRestTemplate,
) : BaseIntegrationTest() {

    private fun headers(userId: Long): HttpHeaders = HttpHeaders().apply {
        set("X-User-Id", userId.toString())
        contentType = MediaType.APPLICATION_JSON
    }

    private fun seedFacility(id: String, ownerUserId: Long) {
        mongoTemplate.save(
            Facility(
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
            )
        )
    }

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE bookings")
            jdbcTemplate.execute("TRUNCATE TABLE slots")
            mongoTemplate.remove(Query(), Facility::class.java)
        }

        Given("[S-01] ownerId=1이 facilityId=FAC-OWNER-01 시설에 슬롯 등록") {
            seedFacility(id = "FAC-OWNER-01", ownerUserId = 1L)
            val requestBody = """
                {
                    "date": "2026-06-01T09:00:00+09:00",
                    "timeRange": "09:00-10:00",
                    "capacity": 5
                }
            """.trimIndent()

            When("POST /facilities/FAC-OWNER-01/slots 호출 시") {
                val response = restTemplate.exchange(
                    "/facilities/FAC-OWNER-01/slots",
                    HttpMethod.POST,
                    HttpEntity(requestBody, headers(1L)),
                    String::class.java,
                )

                Then("[S-01] 201 응답과 슬롯 정보가 반환된다") {
                    response.statusCode shouldBe HttpStatus.CREATED
                    response.body shouldContain "FAC-OWNER-01"
                    response.body shouldContain "09:00-10:00"
                }
            }
        }

        Given("[S-01] GET /facilities/FAC-LIST-01/slots — 슬롯 목록 조회") {
            slotRepository.save(
                Slot.create(
                    facilityId = "FAC-LIST-01",
                    date = ZonedDateTime.now(),
                    timeRange = "09:00-10:00",
                    capacity = 5,
                    ownerId = 1L,
                )
            )
            slotRepository.save(
                Slot.create(
                    facilityId = "FAC-LIST-01",
                    date = ZonedDateTime.now(),
                    timeRange = "10:00-11:00",
                    capacity = 3,
                    ownerId = 1L,
                )
            )

            When("GET /facilities/FAC-LIST-01/slots 호출 시") {
                val response = restTemplate.exchange(
                    "/facilities/FAC-LIST-01/slots",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers(2L)),
                    String::class.java,
                )

                Then("[S-01] 200 응답과 2건의 슬롯이 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    response.body shouldContain "09:00-10:00"
                    response.body shouldContain "10:00-11:00"
                }
            }
        }

        Given("[S-01] ownerId=1이 본인 슬롯 capacity 수정") {
            val slot = slotRepository.save(
                Slot.create(
                    facilityId = "FAC-UPDATE-01",
                    date = ZonedDateTime.now(),
                    timeRange = "09:00-10:00",
                    capacity = 5,
                    ownerId = 1L,
                )
            )
            val patchBody = """{"capacity": 10}"""

            When("PATCH /facilities/FAC-UPDATE-01/slots/{slotId} 호출 시") {
                val response = restTemplate.exchange(
                    "/facilities/FAC-UPDATE-01/slots/${slot.id}",
                    HttpMethod.PATCH,
                    HttpEntity(patchBody, headers(1L)),
                    String::class.java,
                )

                Then("[S-01] 200 응답과 capacity=10이 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    response.body shouldContain "10"
                }
            }
        }

        Given("[S-02] ownerId=2의 슬롯을 userId=1이 수정 시도") {
            val slot = slotRepository.save(
                Slot.create(
                    facilityId = "FAC-UNAUTH-01",
                    date = ZonedDateTime.now(),
                    timeRange = "09:00-10:00",
                    capacity = 5,
                    ownerId = 2L,
                )
            )
            val patchBody = """{"capacity": 99}"""

            When("userId=1이 PATCH /facilities/FAC-UNAUTH-01/slots/{slotId} 호출 시") {
                val response = restTemplate.exchange(
                    "/facilities/FAC-UNAUTH-01/slots/${slot.id}",
                    HttpMethod.PATCH,
                    HttpEntity(patchBody, headers(1L)),
                    String::class.java,
                )

                Then("[S-02] 403 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                }
            }
        }

        Given("[S-03] PENDING Booking이 없는 슬롯 삭제") {
            val slot = slotRepository.save(
                Slot.create(
                    facilityId = "FAC-DELETE-01",
                    date = ZonedDateTime.now(),
                    timeRange = "09:00-10:00",
                    capacity = 5,
                    ownerId = 1L,
                )
            )

            When("DELETE /facilities/FAC-DELETE-01/slots/{slotId} 호출 시") {
                val response = restTemplate.exchange(
                    "/facilities/FAC-DELETE-01/slots/${slot.id}",
                    HttpMethod.DELETE,
                    HttpEntity<Void>(headers(1L)),
                    String::class.java,
                )

                Then("[S-03] 204 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.NO_CONTENT
                }
            }
        }

        Given("[S-03] PENDING Booking이 있는 슬롯 삭제 시도") {
            val slot = slotRepository.save(
                Slot.create(
                    facilityId = "FAC-DELETE-02",
                    date = ZonedDateTime.now(),
                    timeRange = "10:00-11:00",
                    capacity = 5,
                    ownerId = 1L,
                )
            )
            bookingDomainService.createPendingBooking(userId = 10L, slotId = slot.id)

            When("DELETE /facilities/FAC-DELETE-02/slots/{slotId} 호출 시") {
                val response = restTemplate.exchange(
                    "/facilities/FAC-DELETE-02/slots/${slot.id}",
                    HttpMethod.DELETE,
                    HttpEntity<Void>(headers(1L)),
                    String::class.java,
                )

                Then("[S-03] 409 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.CONFLICT
                }
            }
        }
    }
}

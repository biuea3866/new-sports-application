package com.sportsapp.scenario.facility

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.facility.GetFacilityStatsCommand
import com.sportsapp.application.facility.GetFacilityStatsUseCase
import com.sportsapp.application.facility.UpdateFacilityCommand
import com.sportsapp.application.facility.UpdateFacilityUseCase
import com.sportsapp.domain.facility.FacilityDomainService
import com.sportsapp.domain.facility.FacilityAttributes
import com.sportsapp.domain.facility.FacilityNotOwnedByException
import com.sportsapp.domain.facility.Facility
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import java.time.ZonedDateTime

class FacilityUpdateStatsScenarioTest(
    @Autowired private val updateFacilityUseCase: UpdateFacilityUseCase,
    @Autowired private val getFacilityStatsUseCase: GetFacilityStatsUseCase,
    @Autowired private val facilityDomainService: FacilityDomainService,
    @Autowired private val mongoTemplate: MongoTemplate,
) : BaseIntegrationTest() {

    private fun registerFacility(code: String, ownerUserId: Long): Facility {
        val attributes = FacilityAttributes(
            code = code,
            name = "시나리오 테스트 시설 $code",
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
        )
        return facilityDomainService.registerForOwner(attributes, ownerUserId)
    }

    init {
        Given("[S-01] 운영자 본인 시설의 이름을 수정하면 DB에 반영된다") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val facility = registerFacility("SCN-S01-001", ownerUserId = 1L)
            val facilityId = requireNotNull(facility.id) { "facility id must not be null" }

            When("[S-01] UpdateFacilityUseCase 실행") {
                val command = UpdateFacilityCommand(
                    operatorId = 1L,
                    facilityId = facilityId,
                    name = "수정된 시설명",
                )
                val result = updateFacilityUseCase.execute(command)

                Then("[S-01] 반환된 FacilityResponse의 name이 수정된 값이다") {
                    result.name shouldBe "수정된 시설명"
                }
            }
        }

        Given("[S-02] 타인의 시설을 수정 시도하면 도메인 예외가 발생한다") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val facility = registerFacility("SCN-S02-001", ownerUserId = 1L)
            val facilityId = requireNotNull(facility.id) { "facility id must not be null" }

            When("[S-02] operatorId=2로 UpdateFacilityUseCase 실행") {
                Then("[S-02] FacilityNotOwnedByException이 발생한다") {
                    shouldThrow<FacilityNotOwnedByException> {
                        updateFacilityUseCase.execute(
                            UpdateFacilityCommand(
                                operatorId = 2L,
                                facilityId = facilityId,
                                name = "침입 시도",
                            ),
                        )
                    }
                }
            }
        }

        Given("[S-03] GetFacilityStatsUseCase — 등록된 시설에 대한 stats 응답을 반환한다") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val facility = registerFacility("SCN-S03-001", ownerUserId = 1L)
            val facilityId = requireNotNull(facility.id) { "facility id must not be null" }

            When("[S-03] GetFacilityStatsUseCase 실행") {
                val command = GetFacilityStatsCommand(
                    operatorId = 1L,
                    facilityId = facilityId,
                    from = ZonedDateTime.now().minusDays(30),
                    to = ZonedDateTime.now(),
                )
                val result = getFacilityStatsUseCase.execute(command)

                Then("[S-03] facilityId에 해당하는 stats가 반환된다") {
                    result.size shouldBe 1
                    result[0].facilityId shouldBe facilityId
                    result[0].name shouldNotBe null
                    result[0].totalBookings shouldBe 0L
                }
            }
        }
    }
}

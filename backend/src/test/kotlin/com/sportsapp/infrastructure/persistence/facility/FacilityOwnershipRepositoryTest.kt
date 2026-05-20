package com.sportsapp.infrastructure.persistence.facility

import com.sportsapp.BaseMongoIntegrationTest
import com.sportsapp.domain.facility.Facility
import com.sportsapp.domain.facility.FacilityAttributes
import com.sportsapp.domain.facility.FacilityRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query

class FacilityOwnershipRepositoryTest(
    @Autowired private val facilityRepository: FacilityRepository,
    @Autowired private val mongoTemplate: MongoTemplate,
) : BaseMongoIntegrationTest() {

    private fun buildAttributes(code: String, gu: String = "강남구") = FacilityAttributes(
        code = code,
        name = "시설 $code",
        gu = gu,
        type = "수영장",
        address = "서울시 $gu",
        lat = 37.5,
        lng = 127.0,
        parking = true,
        tel = "02-0000-0000",
        homePage = "",
        eduYn = false,
        meta = emptyMap(),
    )

    private fun createFacilityWithOwner(code: String, ownerUserId: Long): Facility {
        val facility = Facility.create(buildAttributes(code))
        facility.assignOwner(ownerUserId)
        return facilityRepository.save(facility)
    }

    init {
        Given("userId=10 시설 2건, userId=20 시설 1건, ownerUserId=null 시설 1건이 저장된 상태") {
            mongoTemplate.remove(Query(), Facility::class.java)
            createFacilityWithOwner("OWN-001", 10L)
            createFacilityWithOwner("OWN-002", 10L)
            createFacilityWithOwner("OWN-003", 20L)
            facilityRepository.save(Facility.create(buildAttributes("ANON-001")))

            When("userId=10으로 findByOwnerUserId를 호출하면") {
                val pageable = PageRequest.of(0, 50)
                val result = facilityRepository.findByOwnerUserId(10L, pageable)
                Then("[R-01] userId=10 시설 2건만 반환되고 다른 사용자 도큐먼트는 제외된다") {
                    result.content shouldHaveSize 2
                    result.content.all { it.ownerUserId == 10L } shouldBe true
                    result.totalElements shouldBe 2
                }
            }

            When("userId=20으로 findByOwnerUserId를 호출하면") {
                val pageable = PageRequest.of(0, 50)
                val result = facilityRepository.findByOwnerUserId(20L, pageable)
                Then("[R-01] userId=20 시설 1건만 반환된다") {
                    result.content shouldHaveSize 1
                    result.content[0].ownerUserId shouldBe 20L
                }
            }
        }

        Given("userId=10이 소유한 시설과 userId=20이 소유한 시설이 저장된 상태") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val ownerFacility = createFacilityWithOwner("OWN-A", 10L)
            createFacilityWithOwner("OWN-B", 20L)

            When("facilityId와 ownerUserId가 일치하지 않는 조건으로 조회하면") {
                val facilityId = requireNotNull(ownerFacility.id) { "saved facility must have id" }
                val result = facilityRepository.findByIdAndOwnerUserId(facilityId, 20L)
                Then("[R-02] null이 반환된다") {
                    result shouldBe null
                }
            }

            When("facilityId와 ownerUserId가 일치하는 조건으로 조회하면") {
                val facilityId = requireNotNull(ownerFacility.id) { "saved facility must have id" }
                val result = facilityRepository.findByIdAndOwnerUserId(facilityId, 10L)
                Then("[R-02] 해당 시설이 반환된다") {
                    result shouldNotBe null
                    result?.ownerUserId shouldBe 10L
                }
            }
        }

        Given("ownerUserId가 null인 기존 시설이 저장된 상태") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val saved = facilityRepository.save(Facility.create(buildAttributes("LEGACY-001")))

            When("id로 findById를 호출하면") {
                val facilityId = requireNotNull(saved.id) { "saved facility must have id" }
                val result = facilityRepository.findById(facilityId)
                Then("[R-03] ownerUserId가 null인 채로 정상 조회된다") {
                    result shouldNotBe null
                    result?.ownerUserId shouldBe null
                    result?.code shouldBe "LEGACY-001"
                }
            }
        }
    }
}

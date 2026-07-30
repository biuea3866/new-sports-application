package com.sportsapp.infrastructure.facility.mongo

import com.sportsapp.BaseMongoIntegrationTest
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.vo.FacilityAttributes
import com.sportsapp.domain.facility.repository.FacilityRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query

class FacilityRepositoryStatsTest(
    @Autowired private val facilityRepository: FacilityRepository,
    @Autowired private val mongoTemplate: MongoTemplate,
) : BaseMongoIntegrationTest() {

    private fun buildAttributes(code: String, gu: String, type: String) = FacilityAttributes(
        code = code,
        name = "시설 $code",
        gu = gu,
        type = type,
        address = "서울시 $gu",
        lat = 37.5,
        lng = 127.0,
        parking = true,
        tel = "02-0000-0000",
        homePage = "",
        eduYn = false,
        meta = emptyMap(),
    )

    init {
        Given("강남구 수영장 3건, 강남구 풋살장 2건, 서초구 헬스장 1건이 저장된 상태") {
            mongoTemplate.remove(Query(), Facility::class.java)
            facilityRepository.save(Facility.create(buildAttributes("GN-SW-001", "강남구", "수영장")))
            facilityRepository.save(Facility.create(buildAttributes("GN-SW-002", "강남구", "수영장")))
            facilityRepository.save(Facility.create(buildAttributes("GN-SW-003", "강남구", "수영장")))
            facilityRepository.save(Facility.create(buildAttributes("GN-FS-001", "강남구", "풋살장")))
            facilityRepository.save(Facility.create(buildAttributes("GN-FS-002", "강남구", "풋살장")))
            facilityRepository.save(Facility.create(buildAttributes("SC-HL-001", "서초구", "헬스장")))

            When("[R-01] aggregateGuType을 호출하면") {
                val result = facilityRepository.aggregateGuType()

                Then("자치구×유형 조합별 정확한 카운트를 반환한다") {
                    result shouldHaveSize 3
                    val gnSwCount = result.first { it.gu == "강남구" && it.type == "수영장" }
                    gnSwCount.count shouldBe 3L
                    val gnFsCount = result.first { it.gu == "강남구" && it.type == "풋살장" }
                    gnFsCount.count shouldBe 2L
                    val scHlCount = result.first { it.gu == "서초구" && it.type == "헬스장" }
                    scHlCount.count shouldBe 1L
                }
            }
        }

        Given("시설 데이터가 없는 상태") {
            mongoTemplate.remove(Query(), Facility::class.java)

            When("[R-02] aggregateGuType을 호출하면") {
                val result = facilityRepository.aggregateGuType()

                Then("빈 결과 리스트를 반환한다") {
                    result shouldHaveSize 0
                }
            }
        }
    }
}

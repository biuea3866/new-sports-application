package com.sportsapp.infrastructure.persistence.facility

import com.sportsapp.BaseMongoIntegrationTest
import com.sportsapp.domain.facility.Facility
import com.sportsapp.domain.facility.FacilityAttributes
import com.sportsapp.domain.facility.FacilityRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query

class FacilityRepositoryImplTest(
    @Autowired private val facilityRepository: FacilityRepository,
    @Autowired private val mongoTemplate: MongoTemplate,
) : BaseMongoIntegrationTest() {

    private fun buildAttributes(
        code: String,
        gu: String,
        type: String,
        lat: Double = 37.5,
        lng: Double = 127.0,
        meta: Map<String, String> = emptyMap(),
    ) = FacilityAttributes(
        code = code,
        name = "시설 $code",
        gu = gu,
        type = type,
        address = "서울시 $gu",
        lat = lat,
        lng = lng,
        parking = true,
        tel = "02-0000-0000",
        homePage = "",
        eduYn = false,
        meta = meta,
    )

    init {
        Given("강남구 시설 3개, 서초구 시설 2개가 저장된 상태") {
            mongoTemplate.remove(Query(), Facility::class.java)
            (1..3).forEach { index ->
                facilityRepository.save(
                    Facility.create(buildAttributes("GN-00$index", "강남구", "수영장", 37.5 + index * 0.001, 127.0 + index * 0.001))
                )
            }
            (1..2).forEach { index ->
                facilityRepository.save(
                    Facility.create(buildAttributes("SC-00$index", "서초구", "헬스장", 37.48 + index * 0.001, 127.01 + index * 0.001))
                )
            }

            When("강남구로 조회하면") {
                val result = facilityRepository.findAllByGu("강남구")
                Then("[R-01] 강남구 시설 3건만 반환된다") {
                    result shouldHaveSize 3
                    result.all { it.gu == "강남구" } shouldBe true
                }
            }

            When("서초구로 조회하면") {
                val result = facilityRepository.findAllByGu("서초구")
                Then("[R-01] 서초구 시설 2건만 반환된다") {
                    result shouldHaveSize 2
                }
            }
        }

        Given("특정 좌표 주변에 가까운 시설과 먼 시설이 저장된 상태") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val centerLat = 37.5665
            val centerLng = 126.9780
            facilityRepository.save(
                Facility.create(buildAttributes("NEAR-001", "중구", "수영장", centerLat + 0.001, centerLng + 0.001))
            )
            facilityRepository.save(
                Facility.create(buildAttributes("FAR-001", "강북구", "헬스장", centerLat + 0.1, centerLng + 0.1))
            )

            When("중심 좌표에서 500m 반경 near 쿼리를 실행하면") {
                val result = facilityRepository.findNear(centerLat, centerLng, 500.0)
                Then("[R-02] 가까운 시설만 반환된다") {
                    result shouldHaveSize 1
                    result[0].code shouldBe "NEAR-001"
                }
            }
        }

        Given("임의 키-값 meta를 가진 시설") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val meta = mapOf(
                "capacity" to "50",
                "lane" to "8",
                "open_time" to "06:00",
                "close_time" to "22:00",
            )
            val saved = facilityRepository.save(
                Facility.create(buildAttributes("META-001", "강남구", "수영장", meta = meta))
            )

            When("저장된 시설을 id로 조회하면") {
                val foundId = requireNotNull(saved.id) { "saved facility must have id" }
                val found = facilityRepository.findById(foundId)
                Then("[R-03] meta 필드가 동일 구조로 복원된다") {
                    found shouldNotBe null
                    found?.meta shouldBe meta
                    found?.meta?.get("capacity") shouldBe "50"
                    found?.meta?.get("lane") shouldBe "8"
                    found?.meta?.get("open_time") shouldBe "06:00"
                    found?.meta?.get("close_time") shouldBe "22:00"
                }
            }
        }
    }
}

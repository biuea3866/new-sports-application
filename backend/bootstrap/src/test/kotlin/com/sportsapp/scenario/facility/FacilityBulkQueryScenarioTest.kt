package com.sportsapp.scenario.facility

import com.sportsapp.BaseMongoIntegrationTest
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.vo.FacilityAttributes
import com.sportsapp.domain.facility.repository.FacilityRepository
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import kotlin.system.measureTimeMillis

class FacilityBulkQueryScenarioTest(
    @Autowired private val facilityRepository: FacilityRepository,
    @Autowired private val mongoTemplate: MongoTemplate,
) : BaseMongoIntegrationTest() {

    init {
        Given("1만건의 시설 데이터가 적재된 상태") {
            mongoTemplate.dropCollection(Facility::class.java)

            val totalCount = 10_000
            val targetGu = "강남구"
            val targetType = "수영장"
            val gangnamSwimmingCount = 2_000

            val facilities = (1..totalCount).map { index ->
                val gu = when {
                    index <= gangnamSwimmingCount -> targetGu
                    index <= 4_000 -> "서초구"
                    index <= 6_000 -> "마포구"
                    index <= 8_000 -> "송파구"
                    else -> "강북구"
                }
                val type = when {
                    index <= gangnamSwimmingCount -> targetType
                    index % 3 == 0 -> "헬스장"
                    index % 3 == 1 -> "테니스장"
                    else -> "농구장"
                }
                Facility.create(
                    FacilityAttributes(
                        code = "BULK-$index",
                        name = "시설 $index",
                        gu = gu,
                        type = type,
                        address = "서울시 $gu $index",
                        lat = 37.0 + (index % 100) * 0.01,
                        lng = 127.0 + (index % 100) * 0.01,
                        parking = index % 2 == 0,
                        tel = "02-0000-$index",
                        homePage = "",
                        eduYn = false,
                        meta = mapOf("capacity" to "${index % 100}"),
                    )
                )
            }
            facilityRepository.saveAll(facilities)

            When("gu + type 복합 필터로 강남구 수영장을 조회하면") {
                var resultSize: Int
                val elapsedMs = measureTimeMillis {
                    val result = facilityRepository.findAllByGuAndType(targetGu, targetType)
                    resultSize = result.size
                }
                Then("[S-01] 강남구 수영장 2000건이 반환되고 Testcontainers 환경 기준 1000ms 이하 기준이 충족된다") {
                    resultSize shouldBe gangnamSwimmingCount
                    // P95 50ms 기준은 프로덕션 기준. Testcontainers 로컬 환경은 1000ms 허용
                    elapsedMs shouldBeLessThan 1_000L
                }
            }
        }
    }
}

package com.sportsapp.infrastructure.facility.mongo

import com.sportsapp.BaseMongoIntegrationTest
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.vo.FacilityAttributes
import com.sportsapp.domain.facility.service.FacilityDomainService
import com.sportsapp.domain.facility.repository.FacilityRepository
import com.sportsapp.domain.facility.dto.LegacyRow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query

class FacilityBulkImportRepositoryTest(
    @Autowired private val facilityRepository: FacilityRepository,
    @Autowired private val facilityDomainService: FacilityDomainService,
    @Autowired private val mongoTemplate: MongoTemplate,
) : BaseMongoIntegrationTest() {

    private fun buildLegacyRow(
        legacyId: String,
        ycode: String = "37.5",
        xcode: String = "127.0",
    ) = LegacyRow(
        legacyId = legacyId,
        name = "시설 $legacyId",
        gu = "강남구",
        type = "수영장",
        address = "서울시 강남구",
        ycode = ycode,
        xcode = xcode,
        parking = true,
        tel = "02-0000-0000",
        homePage = "",
        eduYn = false,
        extraFields = emptyMap(),
    )

    private fun buildAttributes(code: String) = FacilityAttributes(
        code = code,
        name = "시설 $code",
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

    init {
        Given("10건의 레거시 행이 주어졌을 때") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val rows = (1..10).map { buildLegacyRow("LEGACY-$it") }

            When("bulkImport를 실행하면") {
                val result = facilityDomainService.bulkImport(rows)

                Then("[R-01] 10건이 신규 컬렉션에 정확한 필드로 적재된다") {
                    result.insertedCount shouldBe 10
                    result.updatedCount shouldBe 0
                    result.skippedCount shouldBe 0
                    val allSaved = mongoTemplate.count(Query(), Facility::class.java)
                    allSaved shouldBe 10
                }
            }
        }

        Given("좌표가 잘못된 행이 섞인 레거시 데이터가 주어졌을 때") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val rows = listOf(
                buildLegacyRow("VALID-001", ycode = "37.5", xcode = "127.0"),
                buildLegacyRow("INVALID-001", ycode = "NOT_A_NUMBER", xcode = "127.0"),
                buildLegacyRow("VALID-002", ycode = "37.6", xcode = "127.1"),
            )

            When("bulkImport를 실행하면") {
                val result = facilityDomainService.bulkImport(rows)

                Then("유효한 2건만 적재되고 1건은 skippedCount로 계산된다") {
                    result.insertedCount shouldBe 2
                    result.skippedCount shouldBe 1
                    val allSaved = mongoTemplate.count(Query(), Facility::class.java)
                    allSaved shouldBe 2
                }
            }
        }

        Given("동일한 code가 이미 저장된 상태에서") {
            mongoTemplate.remove(Query(), Facility::class.java)
            facilityRepository.save(Facility.create(buildAttributes("EXISTING-001")))

            When("[R-03] 동일한 code로 bulkImport를 두 번 실행하면") {
                val rows = listOf(buildLegacyRow("EXISTING-001"))
                facilityDomainService.bulkImport(rows)
                facilityDomainService.bulkImport(rows)

                Then("컬렉션에는 1건만 존재한다 (no-op upsert)") {
                    val count = mongoTemplate.count(Query(), Facility::class.java)
                    count shouldBe 1
                }
            }
        }

        Given("code가 없는 상태에서 upsertByCode를 호출하면") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val facility = Facility.create(buildAttributes("NEW-CODE-001"))

            When("upsertByCode를 실행하면") {
                val saved = facilityRepository.upsertByCode(facility)

                Then("새 도큐먼트가 삽입되고 조회 가능하다") {
                    saved shouldNotBe null
                    saved.code shouldBe "NEW-CODE-001"
                    facilityRepository.findByCode("NEW-CODE-001") shouldNotBe null
                }
            }
        }
    }
}

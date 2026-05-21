package com.sportsapp.infrastructure.persistence.facility

import com.sportsapp.BaseMongoIntegrationTest
import com.sportsapp.domain.facility.Facility
import com.sportsapp.domain.facility.FacilityAttributes
import com.sportsapp.domain.facility.FacilityRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query

class FacilityUpdateRepositoryTest(
    @Autowired private val facilityRepository: FacilityRepository,
    @Autowired private val mongoTemplate: MongoTemplate,
) : BaseMongoIntegrationTest() {

    private fun buildAttributes(code: String): FacilityAttributes = FacilityAttributes(
        code = code,
        name = "테스트 시설 $code",
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
        Given("[R-01] update 후 저장 시 updatedAt이 갱신되는지 검증") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val original = facilityRepository.save(Facility.create(buildAttributes("UPD-001")).also { it.assignOwner(1L) })
            val originalUpdatedAt = original.updatedAt

            Thread.sleep(100)
            val updated = original.updateInfo(name = "수정된 시설명")
            val saved = facilityRepository.save(updated)

            When("[R-01] 이름 수정 후 save 시") {
                Then("[R-01] name이 수정된 값으로 저장된다") {
                    saved.name shouldBe "수정된 시설명"
                }

                Then("[R-01] updatedAt이 갱신된다") {
                    saved.updatedAt shouldNotBe null
                    saved.updatedAt.isAfter(originalUpdatedAt) shouldBe true
                }
            }
        }
    }
}

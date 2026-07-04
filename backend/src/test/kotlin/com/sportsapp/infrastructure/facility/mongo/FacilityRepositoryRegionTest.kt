package com.sportsapp.infrastructure.facility.mongo

import com.sportsapp.BaseMongoIntegrationTest
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.repository.FacilityRepository
import com.sportsapp.domain.facility.vo.FacilityAttributes
import com.sportsapp.domain.facility.vo.FacilityRegion
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update

class FacilityRepositoryRegionTest(
    @Autowired private val facilityRepository: FacilityRepository,
    @Autowired private val mongoTemplate: MongoTemplate,
) : BaseMongoIntegrationTest() {

    private fun buildAttributes(
        code: String,
        gu: String,
        type: String,
        region: FacilityRegion = FacilityRegion.UNSPECIFIED,
    ) = FacilityAttributes(
        code = code,
        name = "시설 $code",
        gu = gu,
        type = type,
        address = "$gu 어딘가",
        lat = 37.5,
        lng = 127.0,
        parking = true,
        tel = "02-0000-0000",
        homePage = "",
        eduYn = false,
        meta = emptyMap(),
        region = region,
    )

    init {
        Given("region 필드가 없는(legacy) 시설 문서가 저장된 상태") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val saved = facilityRepository.save(Facility.create(buildAttributes("LEGACY-001", "강남구", "수영장")))
            val savedId = requireNotNull(saved.id) { "saved facility must have id" }
            mongoTemplate.updateFirst(
                Query(Criteria.where("id").`is`(savedId)),
                Update()
                    .unset("sido_code")
                    .unset("sido_name")
                    .unset("sigungu_code")
                    .unset("sigungu_name"),
                Facility::class.java,
            )

            When("legacy 문서를 findById로 조회하면") {
                val found = facilityRepository.findById(savedId)
                Then("[R-06] region 4필드가 UNSPECIFIED로 보정되어 반환된다") {
                    found shouldNotBe null
                    found?.sidoCode shouldBe FacilityRegion.UNSPECIFIED.sidoCode
                    found?.sidoName shouldBe FacilityRegion.UNSPECIFIED.sidoName
                    found?.sigunguCode shouldBe FacilityRegion.UNSPECIFIED.sigunguCode
                    found?.sigunguName shouldBe FacilityRegion.UNSPECIFIED.sigunguName
                }
            }
        }

        Given("부산 해운대구 시설과 서울 강남구(동명 없음)·부산과 동일 gu명을 쓰는 서울 시설이 저장된 상태") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val busan = FacilityRegion.of("26", "부산광역시", "26410", "강남구")
            val seoul = FacilityRegion.of("11", "서울특별시", "11680", "강남구")
            facilityRepository.save(Facility.create(buildAttributes("BUSAN-001", "강남구", "수영장", busan)))
            facilityRepository.save(Facility.create(buildAttributes("SEOUL-001", "강남구", "수영장", seoul)))

            When("findAll(sidoCode=부산코드)로 조회하면") {
                val pageable = PageRequest.of(0, 50)
                val result = facilityRepository.findAll(
                    sidoCode = "26",
                    sigunguCode = null,
                    gu = null,
                    type = null,
                    pageable = pageable,
                )
                Then("[R-07] 부산 시설만 반환되고 동명 자치구를 쓰는 서울 시설은 제외된다") {
                    result.content shouldHaveSize 1
                    result.content[0].code shouldBe "BUSAN-001"
                }
            }
        }

        Given("강남구(부산) 수영장 2건, 강남구(부산) 헬스장 1건, 강남구(서울) 수영장 1건이 저장된 상태") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val busan = FacilityRegion.of("26", "부산광역시", "26410", "강남구")
            val seoul = FacilityRegion.of("11", "서울특별시", "11680", "강남구")
            facilityRepository.save(Facility.create(buildAttributes("RT-001", "강남구", "수영장", busan)))
            facilityRepository.save(Facility.create(buildAttributes("RT-002", "강남구", "수영장", busan)))
            facilityRepository.save(Facility.create(buildAttributes("RT-003", "강남구", "헬스장", busan)))
            facilityRepository.save(Facility.create(buildAttributes("RT-004", "강남구", "수영장", seoul)))

            When("[R-08] aggregateRegionType을 호출하면") {
                val result = facilityRepository.aggregateRegionType()
                Then("시도·시군구·유형별 정확한 카운트를 반환한다") {
                    result shouldHaveSize 3
                    val busanSwim = result.first { it.sidoCode == "26" && it.sigunguCode == "26410" && it.type == "수영장" }
                    busanSwim.count shouldBe 2L
                    val busanGym = result.first { it.sidoCode == "26" && it.sigunguCode == "26410" && it.type == "헬스장" }
                    busanGym.count shouldBe 1L
                    val seoulSwim = result.first { it.sidoCode == "11" && it.sigunguCode == "11680" && it.type == "수영장" }
                    seoulSwim.count shouldBe 1L
                }
            }
        }

        Given("정상 시설 2건과 소프트삭제된 시설 1건이 저장된 상태") {
            mongoTemplate.remove(Query(), Facility::class.java)
            facilityRepository.save(Facility.create(buildAttributes("BF-001", "강남구", "수영장")))
            facilityRepository.save(Facility.create(buildAttributes("BF-002", "서초구", "헬스장")))
            val deleted = facilityRepository.save(Facility.create(buildAttributes("BF-003", "마포구", "풋살장")))
                .also { it.softDelete(1L) }
            facilityRepository.save(deleted)

            When("[R-09] findAllForBackfill을 페이징 호출하면") {
                val pageable = PageRequest.of(0, 50)
                val result = facilityRepository.findAllForBackfill(pageable)
                Then("소프트삭제된 시설을 제외한 2건만 반환된다") {
                    result.content shouldHaveSize 2
                    result.content.all { it.code != "BF-003" } shouldBe true
                }
            }
        }
    }
}

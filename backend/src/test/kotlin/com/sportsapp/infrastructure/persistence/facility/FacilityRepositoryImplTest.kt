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
import org.springframework.data.domain.Sort
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

        Given("userId=1 시설 2건, userId=2 시설 1건이 저장된 상태") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val facilityForUser1a = Facility.create(buildAttributes("OWN-001", "강남구", "수영장")).also { it.assignOwner(1L) }
            val facilityForUser1b = Facility.create(buildAttributes("OWN-002", "서초구", "헬스장")).also { it.assignOwner(1L) }
            val facilityForUser2 = Facility.create(buildAttributes("OWN-003", "마포구", "풋살장")).also { it.assignOwner(2L) }
            facilityRepository.save(facilityForUser1a)
            facilityRepository.save(facilityForUser1b)
            facilityRepository.save(facilityForUser2)

            When("userId=1로 findByOwnerUserId 조회하면") {
                val pageable = PageRequest.of(0, 50)
                val result = facilityRepository.findByOwnerUserId(1L, pageable)
                Then("[R-01] userId=1 시설 2건만 반환되고 userId=2 시설은 제외된다") {
                    result.content shouldHaveSize 2
                    result.content.all { it.ownerUserId == 1L } shouldBe true
                }
            }

            When("userId=1의 시설 id로 findByIdAndOwnerUserId를 호출하면") {
                val savedId = requireNotNull(facilityRepository.save(facilityForUser1a).id)
                val result = facilityRepository.findByIdAndOwnerUserId(savedId, 1L)
                Then("[R-02] 해당 시설을 반환한다") {
                    result shouldNotBe null
                }
            }

            When("userId=1의 시설 id로 userId=2를 넘겨 findByIdAndOwnerUserId를 호출하면") {
                val savedId = requireNotNull(facilityRepository.save(facilityForUser1a).id)
                val result = facilityRepository.findByIdAndOwnerUserId(savedId, 2L)
                Then("[R-02] ownerUserId 불일치로 null이 반환된다") {
                    result shouldBe null
                }
            }
        }

        Given("ownerUserId가 NULL인 시설이 저장된 상태") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val saved = facilityRepository.save(Facility.create(buildAttributes("NULL-OWN-001", "강남구", "수영장")))

            When("findById로 조회하면") {
                val foundId = requireNotNull(saved.id)
                val result = facilityRepository.findById(foundId)
                Then("[R-03] ownerUserId NULL 시설도 정상 조회된다") {
                    result shouldNotBe null
                    result?.ownerUserId shouldBe null
                }
            }
        }

        Given("강남구 풋살장 2건, 강남구 수영장 1건, 서초구 풋살장 1건이 저장된 상태") {
            mongoTemplate.remove(Query(), Facility::class.java)
            facilityRepository.save(Facility.create(buildAttributes("GN-FS-001", "강남구", "풋살장")))
            facilityRepository.save(Facility.create(buildAttributes("GN-FS-002", "강남구", "풋살장")))
            facilityRepository.save(Facility.create(buildAttributes("GN-SW-001", "강남구", "수영장")))
            facilityRepository.save(Facility.create(buildAttributes("SC-FS-001", "서초구", "풋살장")))

            When("gu=강남구, type=풋살장으로 페이징 조회하면") {
                val pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "name"))
                val result = facilityRepository.findAll("강남구", "풋살장", pageable)
                Then("[R-01] gu와 type 두 필터를 모두 만족하는 2건만 반환된다") {
                    result.content shouldHaveSize 2
                    result.content.all { it.gu == "강남구" && it.type == "풋살장" } shouldBe true
                    result.totalElements shouldBe 2
                }
            }

            When("필터 없이 페이징 조회하면") {
                val pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "name"))
                val result = facilityRepository.findAll(null, null, pageable)
                Then("[R-03] 전체 4건이 페이지네이션 카운트로 반환된다") {
                    result.totalElements shouldBe 4
                }
            }

            When("name asc 정렬 페이징 조회하면") {
                val pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "name"))
                val result = facilityRepository.findAll(null, null, pageable)
                Then("[R-02] 결과가 name asc 안정 정렬로 반환된다") {
                    val names = result.content.map { it.name }
                    names shouldBe names.sorted()
                }
            }
        }
    }
}

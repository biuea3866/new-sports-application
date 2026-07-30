package com.sportsapp.scenario.facility

import com.sportsapp.BaseMongoIntegrationTest
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.vo.FacilityAttributes
import com.sportsapp.domain.facility.vo.FacilityRegion
import com.sportsapp.domain.facility.repository.FacilityRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class FacilityApiScenarioTest(
    @Autowired private val mockMvc: MockMvc,
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
        address = "서울시 $gu",
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
        Given("강남구 수영장 2건, 서초구 헬스장 1건이 저장된 상태") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val saved1 = facilityRepository.save(Facility.create(buildAttributes("GN-SW-001", "강남구", "수영장")))
            facilityRepository.save(Facility.create(buildAttributes("GN-SW-002", "강남구", "수영장")))
            facilityRepository.save(Facility.create(buildAttributes("SC-HL-001", "서초구", "헬스장")))

            When("[S-01] GET /facilities/stats/gu-type 요청 시") {
                val response = mockMvc.perform(
                    get("/facilities/stats/gu-type")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("200 OK와 [{gu, type, count}] 배열이 반환된다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$").isArray)
                        .andExpect(jsonPath("$.length()").value(2))
                }
            }

            When("[S-02] 존재하지 않는 시설 ID로 GET /facilities/{id} 요청 시") {
                val response = mockMvc.perform(
                    get("/facilities/nonexistent-id-12345")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("404 응답이 반환된다") {
                    response.andExpect(status().isNotFound)
                }
            }

            When("존재하는 시설 ID로 GET /facilities/{id} 요청 시") {
                val facilityId = requireNotNull(saved1.id) { "saved facility must have id" }
                val response = mockMvc.perform(
                    get("/facilities/$facilityId")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("200 OK와 시설 단건 응답이 반환된다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.id").value(facilityId))
                        .andExpect(jsonPath("$.gu").value("강남구"))
                        .andExpect(jsonPath("$.type").value("수영장"))
                }
            }
        }

        Given("부산 해운대구 수영장 2건, 서울 강남구 헬스장 1건이 저장된 상태") {
            mongoTemplate.remove(Query(), Facility::class.java)
            val busan = FacilityRegion.of("26", "부산광역시", "26410", "해운대구")
            val seoul = FacilityRegion.of("11", "서울특별시", "11680", "강남구")
            facilityRepository.save(Facility.create(buildAttributes("BS-SW-001", "해운대구", "수영장", busan)))
            facilityRepository.save(Facility.create(buildAttributes("BS-SW-002", "해운대구", "수영장", busan)))
            facilityRepository.save(Facility.create(buildAttributes("SL-HL-001", "강남구", "헬스장", seoul)))

            When("GET /facilities/stats/region-type 요청 시") {
                val response = mockMvc.perform(
                    get("/facilities/stats/region-type")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("200 OK와 시도·시군구·유형별 count 배열이 반환된다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$").isArray)
                        .andExpect(jsonPath("$.length()").value(2))
                }
            }

            When("기존 GET /facilities/stats/gu-type 요청 시") {
                val response = mockMvc.perform(
                    get("/facilities/stats/gu-type")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("region 필터 도입 이후에도 기존과 동일하게 동작한다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$").isArray)
                        .andExpect(jsonPath("$.length()").value(2))
                }
            }
        }
    }
}

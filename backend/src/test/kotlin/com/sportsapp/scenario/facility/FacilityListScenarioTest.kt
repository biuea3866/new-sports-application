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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class FacilityListScenarioTest(
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
        Given("강남구 풋살장 3건, 서초구 헬스장 2건이 저장된 상태") {
            mongoTemplate.dropCollection(Facility::class.java)
            (1..3).forEach { index ->
                facilityRepository.save(Facility.create(buildAttributes("GN-FS-00$index", "강남구", "풋살장")))
            }
            (1..2).forEach { index ->
                facilityRepository.save(Facility.create(buildAttributes("SC-HS-00$index", "서초구", "헬스장")))
            }

            When("[S-01] GET /facilities?gu=강남구&page=0&size=20 요청 시") {
                val response = mockMvc.perform(
                    get("/facilities")
                        .param("gu", "강남구")
                        .param("page", "0")
                        .param("size", "20")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("200 OK와 강남구 시설 3건이 담긴 Page 응답이 반환된다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(3))
                        .andExpect(jsonPath("$.content[0].gu").value("강남구"))
                }
            }

            When("[S-02] 인증 없이 GET /facilities?gu=강남구&type=풋살장 요청 시") {
                val response = mockMvc.perform(
                    get("/facilities")
                        .param("gu", "강남구")
                        .param("type", "풋살장")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("인증 없이도 200 OK와 풋살장 3건이 반환된다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(3))
                }
            }

            When("GET /facilities (필터 없이 전체 조회)") {
                val response = mockMvc.perform(
                    get("/facilities")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("전체 5건이 반환된다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(5))
                }
            }

            When("region 정보 없이 저장된 레거시 시설을 GET /facilities로 조회하면") {
                val response = mockMvc.perform(
                    get("/facilities")
                        .param("gu", "강남구")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("응답의 sidoName·sigunguName이 미지정으로 노출된다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.content[0].sidoCode").value(FacilityRegion.UNSPECIFIED.sidoCode))
                        .andExpect(jsonPath("$.content[0].sidoName").value(FacilityRegion.UNSPECIFIED.sidoName))
                        .andExpect(jsonPath("$.content[0].sigunguCode").value(FacilityRegion.UNSPECIFIED.sigunguCode))
                        .andExpect(jsonPath("$.content[0].sigunguName").value(FacilityRegion.UNSPECIFIED.sigunguName))
                }
            }
        }

        Given("부산 해운대구 시설 1건, 서울 강남구(부산과 동일한 gu명) 시설 1건이 저장된 상태") {
            mongoTemplate.dropCollection(Facility::class.java)
            val busan = FacilityRegion.of("26", "부산광역시", "26410", "해운대구")
            val seoul = FacilityRegion.of("11", "서울특별시", "11680", "강남구")
            facilityRepository.save(Facility.create(buildAttributes("BUSAN-001", "해운대구", "수영장", busan)))
            facilityRepository.save(Facility.create(buildAttributes("SEOUL-001", "강남구", "수영장", seoul)))

            When("GET /facilities?sidoCode=26 요청 시") {
                val response = mockMvc.perform(
                    get("/facilities")
                        .param("sidoCode", "26")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("부산 시설 1건만 반환되고 지역 4필드가 응답에 포함된다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(1))
                        .andExpect(jsonPath("$.content[0].sidoCode").value("26"))
                        .andExpect(jsonPath("$.content[0].sidoName").value("부산광역시"))
                        .andExpect(jsonPath("$.content[0].sigunguCode").value("26410"))
                        .andExpect(jsonPath("$.content[0].sigunguName").value("해운대구"))
                }
            }

            When("GET /facilities?sidoCode=26&sigunguCode=26410 요청 시") {
                val response = mockMvc.perform(
                    get("/facilities")
                        .param("sidoCode", "26")
                        .param("sigunguCode", "26410")
                        .accept(MediaType.APPLICATION_JSON)
                )

                Then("시도·시군구 필터에 정확히 일치하는 시설만 반환된다") {
                    response
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(1))
                        .andExpect(jsonPath("$.content[0].gu").value("해운대구"))
                }
            }
        }
    }
}

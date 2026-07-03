package com.sportsapp.infrastructure.facility.gateway

import com.sportsapp.infrastructure.external.ExternalContractSupport
import com.sportsapp.infrastructure.external.ExternalRestClientFactory
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/**
 * data.go.kr 공공체육시설 목록 API 계약 검증(BE-02, ADR-002).
 *
 * MockWebServer + 녹화 fixture 로 `DataGoKrPublicFacilityGatewayImpl` 의 HTTP 경로·역직렬화·매핑이
 * mock 스키마와 어긋나지 않는지 CI 에서 상시 감시한다. 실 API 는 호출하지 않는다(live 태그 스펙 별도).
 */
class DataGoKrPublicFacilityContractTest : BehaviorSpec({

    val mockWebServers = mutableListOf<MockWebServer>()

    afterSpec {
        mockWebServers.forEach { it.shutdown() }
    }

    fun gatewayCallingMockServer(mockWebServer: MockWebServer): DataGoKrPublicFacilityGatewayImpl {
        mockWebServers += mockWebServer
        val properties = PublicFacilityProperties(
            baseUrl = mockWebServer.url("/").toString().removeSuffix("/"),
            apiKey = "contract-test-service-key",
        )
        return DataGoKrPublicFacilityGatewayImpl(ExternalRestClientFactory(), properties)
    }

    Given("data.go.kr 공공체육시설 fixture 응답이 준비된 상태에서") {
        val mockWebServer = ExternalContractSupport.startMockServer()
        val fixtureBody = ExternalContractSupport.loadFixture("external/data-go-kr/public-sports-facility.json")
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(fixtureBody),
        )
        val gateway = gatewayCallingMockServer(mockWebServer)

        When("fetchPage(1, 3) 을 호출하면") {
            val facilities = gateway.fetchPage(1, 3)

            Then("item[] 이 PublicFacility 로 매핑되며 externalId·lat·lng 이 정확히 채워진다") {
                facilities shouldHaveSize 3

                val firstFacility = facilities.first()
                firstFacility.externalId shouldBe "PUB-00001"
                firstFacility.name shouldBe "강남구 공공축구장 1호"
                firstFacility.gu shouldBe "강남구"
                firstFacility.type shouldBe "축구장"
                firstFacility.address shouldBe "서울특별시 강남구 스포츠로 1"
                firstFacility.lat shouldBe 37.451
                firstFacility.lng shouldBe 126.901
                firstFacility.tel shouldBe "02-1001-0001"
            }

            Then("요청한 numOfRows 만큼 페이지 항목이 반환된다") {
                facilities shouldHaveSize 3
            }
        }
    }

    Given("data.go.kr 이 resultCode≠00(오류) 응답을 반환하는 상태에서") {
        val mockWebServer = ExternalContractSupport.startMockServer()
        val errorResponseBody = """
            {
              "response": {
                "header": { "resultCode": "03", "resultMsg": "NODATA_ERROR" },
                "body": { "pageNo": 1, "numOfRows": 10, "totalCount": 0 }
              }
            }
        """.trimIndent()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(errorResponseBody),
        )
        val gateway = gatewayCallingMockServer(mockWebServer)

        When("fetchPage(1, 10) 을 호출하면") {
            val facilities = gateway.fetchPage(1, 10)

            Then("emptyList 로 degrade 한다") {
                facilities.shouldBeEmpty()
            }
        }
    }

    Given("data.go.kr 이 빈 item[] 응답을 반환하는 상태에서") {
        val mockWebServer = ExternalContractSupport.startMockServer()
        val emptyItemsResponseBody = """
            {
              "response": {
                "header": { "resultCode": "00", "resultMsg": "NORMAL SERVICE" },
                "body": { "pageNo": 1, "numOfRows": 10, "totalCount": 0, "items": { "item": [] } }
              }
            }
        """.trimIndent()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(emptyItemsResponseBody),
        )
        val gateway = gatewayCallingMockServer(mockWebServer)

        When("fetchPage(1, 10) 을 호출하면") {
            val facilities = gateway.fetchPage(1, 10)

            Then("emptyList 를 반환한다") {
                facilities.shouldBeEmpty()
            }
        }
    }

    Given("data.go.kr 이 5xx 를 반환하는 상태에서") {
        val mockWebServer = ExternalContractSupport.startMockServer()
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        val gateway = gatewayCallingMockServer(mockWebServer)

        When("fetchPage(1, 10) 을 호출하면") {
            val facilities = gateway.fetchPage(1, 10)

            Then("예외를 전파하지 않고 emptyList 로 degrade 한다") {
                facilities.shouldBeEmpty()
            }
        }
    }
})

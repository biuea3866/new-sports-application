package com.sportsapp.infrastructure.facility.gateway

import com.sportsapp.infrastructure.external.ExternalContractSupport
import com.sportsapp.infrastructure.external.ExternalRestClientFactory
import com.sportsapp.infrastructure.external.Live
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

private const val SAMPLE_ADDRESS = "서울 강남구 테헤란로 427"
private const val TEST_API_KEY = "test-kakao-rest-api-key"
private const val LIVE_API_KEY_ENV = "KAKAO_REST_API_KEY"
private const val LIVE_BASE_URL_ENV = "EXTERNAL_GEOCODING_BASE_URL"
private const val LIVE_DEFAULT_BASE_URL = "https://dapi.kakao.com"

private fun gatewayFor(mockWebServer: MockWebServer, apiKey: String = TEST_API_KEY): KakaoGeocodingGatewayImpl {
    val properties = GeocodingProperties(
        baseUrl = mockWebServer.url("/").toString(),
        apiKey = apiKey,
    )
    return KakaoGeocodingGatewayImpl(ExternalRestClientFactory(), properties)
}

/**
 * Kakao Local REST API(`documents[].x/y`) 매핑을 락하는 계약 테스트.
 * MockWebServer 로 fixture 를 enqueue 해 실 응답 스키마와의 계약을 검증한다.
 */
class KakaoGeocodingContractTest : BehaviorSpec({

    Given("fixture 가 documents[0] 에 x(경도)·y(위도) 를 담아 응답하면") {
        When("geocode 를 호출하면") {
            Then("y 는 lat, x 는 lng 로 매핑된 Coordinate 를 반환한다") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                val fixture = ExternalContractSupport.loadFixture("external/kakao-local/address.json")
                mockWebServer.enqueue(
                    MockResponse().setBody(fixture).setHeader("Content-Type", "application/json"),
                )
                val gateway = gatewayFor(mockWebServer)

                val coordinate = gateway.geocode(SAMPLE_ADDRESS)

                coordinate shouldNotBe null
                requireNotNull(coordinate).lat shouldBe 37.5052427
                coordinate.lng shouldBe 127.0533365

                mockWebServer.shutdown()
            }
        }
    }

    Given("api-key 가 설정돼 있으면") {
        When("geocode 를 호출하면") {
            Then("Authorization: KakaoAK {key} 헤더가 전송된다") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                val fixture = ExternalContractSupport.loadFixture("external/kakao-local/address.json")
                mockWebServer.enqueue(
                    MockResponse().setBody(fixture).setHeader("Content-Type", "application/json"),
                )
                val gateway = gatewayFor(mockWebServer)

                gateway.geocode(SAMPLE_ADDRESS)

                val recordedRequest = mockWebServer.takeRequest()
                recordedRequest.getHeader("Authorization") shouldBe "KakaoAK $TEST_API_KEY"

                mockWebServer.shutdown()
            }
        }
    }

    Given("주소가 빈 문자열이면") {
        When("geocode 를 호출하면") {
            Then("API 를 호출하지 않고 null 을 반환한다") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                val gateway = gatewayFor(mockWebServer)

                val coordinate = gateway.geocode("")

                coordinate shouldBe null
                mockWebServer.requestCount shouldBe 0

                mockWebServer.shutdown()
            }
        }
    }

    Given("documents 가 빈 배열로 응답하면") {
        When("geocode 를 호출하면") {
            Then("null 로 degrade 한다") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                mockWebServer.enqueue(
                    MockResponse()
                        .setBody(
                            """{"meta":{"total_count":0,"pageable_count":0,"is_end":true},"documents":[]}""",
                        )
                        .setHeader("Content-Type", "application/json"),
                )
                val gateway = gatewayFor(mockWebServer)

                val coordinate = gateway.geocode(SAMPLE_ADDRESS)

                coordinate shouldBe null

                mockWebServer.shutdown()
            }
        }
    }

    Given("Kakao Local API 가 5xx 를 반환하면") {
        When("geocode 를 호출하면") {
            Then("예외를 전파하지 않고 null 로 degrade 한다") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                mockWebServer.enqueue(MockResponse().setResponseCode(500))
                val gateway = gatewayFor(mockWebServer)

                val coordinate = gateway.geocode(SAMPLE_ADDRESS)

                coordinate shouldBe null

                mockWebServer.shutdown()
            }
        }
    }
})

/**
 * 실 Kakao Local API 스모크(ADR-002 live 태그).
 * `KAKAO_REST_API_KEY` 가 없으면 [ExternalContractSupport.requireLiveKey] 가 null 을 반환해
 * 검증 없이 통과 처리된다.
 */
class KakaoGeocodingLiveContractTest : BehaviorSpec({

    Given("KAKAO_REST_API_KEY 가 env 에 설정돼 있으면") {
        When("실 주소로 geocode 를 호출하면") {
            Then("documents[].x/y 를 무손실로 역직렬화한 Coordinate 를 반환한다") {
                val liveApiKey = ExternalContractSupport.requireLiveKey(LIVE_API_KEY_ENV)

                if (liveApiKey != null) {
                    val liveBaseUrl = System.getenv(LIVE_BASE_URL_ENV)?.takeIf { it.isNotBlank() }
                        ?: LIVE_DEFAULT_BASE_URL
                    val properties = GeocodingProperties(baseUrl = liveBaseUrl, apiKey = liveApiKey)
                    val gateway = KakaoGeocodingGatewayImpl(ExternalRestClientFactory(), properties)

                    val coordinate = gateway.geocode(SAMPLE_ADDRESS)

                    coordinate shouldNotBe null
                }
            }
        }
    }
}) {
    override fun tags() = setOf(Live)
}

package com.sportsapp.infrastructure.weather.gateway

import com.sportsapp.domain.weather.vo.PrecipitationType
import com.sportsapp.domain.weather.vo.SkyState
import com.sportsapp.infrastructure.external.ExternalContractSupport
import com.sportsapp.infrastructure.external.ExternalRestClientFactory
import com.sportsapp.infrastructure.external.Live
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

private const val TEST_API_KEY = "test-data-go-kr-service-key"
private const val LIVE_API_KEY_ENV = "DATA_GO_KR_SERVICE_KEY"
private const val LIVE_BASE_URL_ENV = "EXTERNAL_WEATHER_BASE_URL"
private const val LIVE_DEFAULT_BASE_URL = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0"

/**
 * 기상청 단기예보(getVilageFcst) 응답 매핑을 락하는 계약 테스트 + 실패 경로 graceful degradation 검증(ADR-003).
 * MockWebServer 로 fixture 를 enqueue 해 실 응답 스키마와의 계약을 검증한다.
 */
class KmaWeatherContractTest : BehaviorSpec({

    suspend fun withGateway(
        mockWebServer: MockWebServer,
        apiKey: String = TEST_API_KEY,
        block: suspend (KmaWeatherGatewayImpl) -> Unit,
    ) {
        val properties = WeatherProperties(
            baseUrl = mockWebServer.url("/").toString(),
            apiKey = apiKey,
        )
        val gateway = KmaWeatherGatewayImpl(ExternalRestClientFactory(), properties)
        block(gateway)
    }

    Given("fixture 가 category 별 값을 동일 fcstDate·fcstTime 조합으로 응답하면") {
        When("shortForecast 를 호출하면") {
            Then("category 별 값이 ForecastSlot 하나로 그룹핑돼 매핑된다") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                val fixture = ExternalContractSupport.loadFixture("external/data-go-kr/vilage-fcst.json")
                mockWebServer.enqueue(
                    MockResponse().setHeader("Content-Type", "application/json").setBody(fixture),
                )

                withGateway(mockWebServer) { gateway ->
                    val forecast = gateway.shortForecast(37.5665, 126.9780)

                    forecast.slots shouldHaveSize 2
                    val morningSlot = forecast.slots.first { it.time == "0900" }
                    morningSlot.date shouldBe "20260703"
                    requireNotNull(morningSlot.temperature) shouldBeExactly 18.0
                    morningSlot.sky shouldBe SkyState.CLEAR
                    morningSlot.precipitationType shouldBe PrecipitationType.NONE
                    morningSlot.precipitationProbability shouldBe 10
                    morningSlot.humidity shouldBe 45
                    requireNotNull(morningSlot.windSpeed) shouldBeExactly 1.8

                    val noonSlot = forecast.slots.first { it.time == "1200" }
                    requireNotNull(noonSlot.temperature) shouldBeExactly 23.0
                    noonSlot.sky shouldBe SkyState.MOSTLY_CLOUDY
                    noonSlot.precipitationType shouldBe PrecipitationType.RAIN
                    noonSlot.precipitationProbability shouldBe 60
                    noonSlot.humidity shouldBe 70
                    requireNotNull(noonSlot.windSpeed) shouldBeExactly 3.2
                }

                mockWebServer.shutdown()
            }

            Then("슬롯이 date·time 오름차순으로 정렬돼 반환된다") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                val fixture = ExternalContractSupport.loadFixture("external/data-go-kr/vilage-fcst.json")
                mockWebServer.enqueue(
                    MockResponse().setHeader("Content-Type", "application/json").setBody(fixture),
                )

                withGateway(mockWebServer) { gateway ->
                    val forecast = gateway.shortForecast(37.5665, 126.9780)

                    forecast.slots.map { it.time } shouldBe listOf("0900", "1200")
                }

                mockWebServer.shutdown()
            }
        }
    }

    Given("기상청 서버가 5xx 를 반환할 때") {
        When("shortForecast 를 호출하면") {
            Then("예외를 전파하지 않고 빈 Forecast 로 degrade 한다") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                mockWebServer.enqueue(MockResponse().setResponseCode(500))

                withGateway(mockWebServer) { gateway ->
                    val forecast = gateway.shortForecast(37.5665, 126.9780)

                    forecast.slots shouldHaveSize 0
                }

                mockWebServer.shutdown()
            }
        }
    }

    Given("resultCode 가 00 이 아닌 응답을 받으면") {
        When("shortForecast 를 호출하면") {
            Then("item 이 존재해도 빈 Forecast 로 degrade 한다") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                mockWebServer.enqueue(
                    MockResponse().setHeader("Content-Type", "application/json").setBody(
                        """
                        {"response":{"header":{"resultCode":"03","resultMsg":"NODATA_ERROR"},
                        "body":{"items":{"item":[
                        {"category":"TMP","fcstDate":"20260703","fcstTime":"0900","fcstValue":"18"}
                        ]}}}}
                        """.trimIndent(),
                    ),
                )

                withGateway(mockWebServer) { gateway ->
                    val forecast = gateway.shortForecast(37.5665, 126.9780)

                    forecast.slots shouldHaveSize 0
                }

                mockWebServer.shutdown()
            }
        }
    }

    Given("item 배열이 빈 배열로 응답하면") {
        When("shortForecast 를 호출하면") {
            Then("빈 Forecast 로 degrade 한다") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                mockWebServer.enqueue(
                    MockResponse().setHeader("Content-Type", "application/json").setBody(
                        """{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE"},"body":{"items":{"item":[]}}}}""",
                    ),
                )

                withGateway(mockWebServer) { gateway ->
                    val forecast = gateway.shortForecast(37.5665, 126.9780)

                    forecast.slots shouldHaveSize 0
                }

                mockWebServer.shutdown()
            }
        }
    }
})

/**
 * 실 기상청 단기예보 API 스모크(ADR-002 live 태그).
 * `DATA_GO_KR_SERVICE_KEY` 가 없으면 [ExternalContractSupport.requireLiveKey] 가 null 을 반환해 검증 없이 통과 처리된다.
 */
class KmaWeatherLiveContractTest : BehaviorSpec({

    Given("DATA_GO_KR_SERVICE_KEY 가 env 에 설정돼 있으면") {
        When("실 좌표로 shortForecast 를 호출하면") {
            Then("응답을 무손실로 역직렬화한 Forecast 를 반환한다") {
                val liveApiKey = ExternalContractSupport.requireLiveKey(LIVE_API_KEY_ENV)
                if (liveApiKey == null) {
                    return@Then
                }

                val liveBaseUrl = System.getenv(LIVE_BASE_URL_ENV)?.takeIf { it.isNotBlank() }
                    ?: LIVE_DEFAULT_BASE_URL
                val properties = WeatherProperties(baseUrl = liveBaseUrl, apiKey = liveApiKey)
                val gateway = KmaWeatherGatewayImpl(ExternalRestClientFactory(), properties)

                val forecast = gateway.shortForecast(37.5665, 126.9780)

                forecast.slots.shouldNotBeEmpty()
                forecast.slots.forEach { slot ->
                    slot.date.shouldNotBeBlank()
                    slot.time.shouldNotBeBlank()
                }
            }
        }
    }
}) {
    override fun tags() = setOf(Live)
}

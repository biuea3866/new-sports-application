package com.sportsapp.infrastructure.airquality.gateway

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.infrastructure.airquality.redis.AirQualityRedisCache
import com.sportsapp.infrastructure.external.ExternalContractSupport
import com.sportsapp.infrastructure.external.ExternalRestClientFactory
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate

private const val TEST_API_KEY = "test-air-korea-service-key"

private fun nearbyStationResponse(stationName: String): MockResponse = MockResponse()
    .setHeader("Content-Type", "application/json")
    .setBody(
        """{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE"},
        "body":{"items":{"item":[{"stationName":"$stationName","tm":"0.32"}]}}}}""",
    )

private fun realtimeMeasureResponse(stationName: String, pm10: String, pm25: String, dataTime: String): MockResponse =
    MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(
            """{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE"},
            "body":{"items":{"item":[{"stationName":"$stationName","pm10Value":"$pm10","pm25Value":"$pm25","dataTime":"$dataTime"}]}}}}""",
        )

/**
 * 에어코리아 2단계 체인([AirKoreaTmProjection]으로 좌표 변환 → getNearbyMsrstnList
 * → getMsrstnAcctoRltmMesureDnsty) 계약 테스트.
 * mock 서버(MockWebServer) + 실 Redis(TestContainers, AirQualityRedisCache)로 캐시 히트/미스·장애 degrade 를 검증한다.
 */
class AirKoreaAirQualityGatewayImplContractTest(
    @Autowired private val airQualityRedisCache: AirQualityRedisCache,
    @Autowired private val stringRedisTemplate: StringRedisTemplate,
) : BaseIntegrationTest() {

    private fun withGateway(
        mockWebServer: MockWebServer,
        block: (AirKoreaAirQualityGatewayImpl) -> Unit,
    ) {
        val properties = AirQualityProperties(
            baseUrl = mockWebServer.url("/").toString(),
            apiKey = TEST_API_KEY,
        )
        val gateway = AirKoreaAirQualityGatewayImpl(
            ExternalRestClientFactory(),
            properties,
            airQualityRedisCache,
        )
        block(gateway)
    }

    private fun evictCacheFor(lat: Double, lng: Double) {
        val gridKey = AirQualityGridKey.of(lat, lng)
        stringRedisTemplate.unlink("airquality:measurement:$gridKey")
    }

    init {
        Given("2단계 체인이 모두 정상 응답하면") {
            val lat = 37.5665
            val lng = 126.9780
            evictCacheFor(lat, lng)
            val expectedCoordinate = AirKoreaTmProjection.toTm(lat, lng)

            When("current 를 호출하면") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                mockWebServer.enqueue(nearbyStationResponse("중구"))
                mockWebServer.enqueue(realtimeMeasureResponse("중구", "42", "18", "2026-07-04 09:15"))

                lateinit var measurement: com.sportsapp.domain.airquality.vo.AirQualityMeasurement
                withGateway(mockWebServer) { gateway -> measurement = gateway.current(lat, lng) }

                Then("2개 요청이 순서대로 호출되어 pm10·pm25·측정소·측정시각을 파싱한다") {
                    mockWebServer.requestCount shouldBe 2
                    measurement.pm10 shouldBe 42
                    measurement.pm25 shouldBe 18
                    measurement.stationName shouldBe "중구"
                    measurement.measuredAt.shouldNotBeNull()
                    measurement.measuredAt?.hour shouldBe 9
                    measurement.measuredAt?.minute shouldBe 15
                }

                Then("nearby 요청은 WGS84→TM 변환된 tmX·tmY, realtime 요청은 ver=1.3·dataTerm=DAILY 를 포함한다") {
                    // 요청 순서대로 정확히 2건만 소비한다 (takeRequest 를 요청 수보다 많이 호출하면 블로킹된다).
                    val nearbyRequest = mockWebServer.takeRequest()
                    nearbyRequest.path.shouldNotBeNull()
                    nearbyRequest.path?.contains("getNearbyMsrstnList") shouldBe true
                    nearbyRequest.requestUrl?.queryParameter("tmX") shouldBe expectedCoordinate.tmX
                    nearbyRequest.requestUrl?.queryParameter("tmY") shouldBe expectedCoordinate.tmY

                    val realtimeMeasureRequest = mockWebServer.takeRequest()
                    realtimeMeasureRequest.path.shouldNotBeNull()
                    realtimeMeasureRequest.path?.contains("getMsrstnAcctoRltmMesureDnsty") shouldBe true
                    realtimeMeasureRequest.requestUrl?.queryParameter("ver") shouldBe "1.3"
                    realtimeMeasureRequest.requestUrl?.queryParameter("dataTerm") shouldBe "DAILY"
                }

                mockWebServer.shutdown()
            }
        }

        Given("동일 좌표를 캐시에 먼저 저장해 둔 상태에서") {
            val lat = 35.1587
            val lng = 129.1604
            evictCacheFor(lat, lng)
            val gridKey = AirQualityGridKey.of(lat, lng)
            airQualityRedisCache.save(
                gridKey,
                com.sportsapp.domain.airquality.vo.AirQualityMeasurement(
                    pm10 = 55,
                    pm25 = 20,
                    stationName = "해운대구",
                    measuredAt = null,
                ),
            )

            When("current 를 호출하면") {
                val mockWebServer = ExternalContractSupport.startMockServer()

                lateinit var measurement: com.sportsapp.domain.airquality.vo.AirQualityMeasurement
                withGateway(mockWebServer) { gateway -> measurement = gateway.current(lat, lng) }

                Then("외부 API를 0회 호출하고 캐시 값을 그대로 반환한다") {
                    mockWebServer.requestCount shouldBe 0
                    measurement.pm10 shouldBe 55
                    measurement.pm25 shouldBe 20
                    measurement.stationName shouldBe "해운대구"
                }

                mockWebServer.shutdown()
            }
        }

        Given("getNearbyMsrstnList 단계가 5xx 를 반환하면") {
            val lat = 37.5003
            val lng = 127.0002
            evictCacheFor(lat, lng)

            When("current 를 호출하면") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                mockWebServer.enqueue(MockResponse().setResponseCode(500))

                lateinit var measurement: com.sportsapp.domain.airquality.vo.AirQualityMeasurement
                withGateway(mockWebServer) { gateway -> measurement = gateway.current(lat, lng) }

                Then("예외 전파 없이 AirQualityMeasurement.empty() 로 degrade 한다") {
                    measurement.pm10.shouldBeNull()
                    measurement.pm25.shouldBeNull()
                    measurement.stationName.shouldBeNull()
                    measurement.measuredAt.shouldBeNull()
                }

                mockWebServer.shutdown()
            }
        }

        Given("1단계(getNearbyMsrstnList) 호출이 read 타임아웃 내에 응답하지 않으면") {
            val lat = 36.1000
            val lng = 128.4000
            evictCacheFor(lat, lng)

            When("current 를 호출하면") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

                lateinit var measurement: com.sportsapp.domain.airquality.vo.AirQualityMeasurement
                withGateway(mockWebServer) { gateway -> measurement = gateway.current(lat, lng) }

                Then("예외 전파 없이 empty 로 degrade 한다") {
                    measurement.pm10.shouldBeNull()
                    measurement.stationName.shouldBeNull()
                }

                mockWebServer.shutdown()
            }
        }

        Given("동일 그리드키로 반복 요청하면") {
            val lat = 33.4996
            val lng = 126.5312
            evictCacheFor(lat, lng)

            When("연속으로 3회 current 를 호출하면") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                mockWebServer.enqueue(nearbyStationResponse("제주"))
                mockWebServer.enqueue(realtimeMeasureResponse("제주", "30", "12", "2026-07-04 10:00"))

                val results = mutableListOf<com.sportsapp.domain.airquality.vo.AirQualityMeasurement>()
                withGateway(mockWebServer) { gateway ->
                    repeat(3) { results.add(gateway.current(lat, lng)) }
                }

                Then("TTL 내 캐시로 수렴해 외부 API는 최초 1회(2요청)만 호출된다(멱등)") {
                    mockWebServer.requestCount shouldBe 2
                    results.map { it.pm10 }.toSet() shouldBe setOf(30)
                    results.map { it.stationName }.toSet() shouldBe setOf("제주")

                    val gridKey = AirQualityGridKey.of(lat, lng)
                    val ttl = stringRedisTemplate.getExpire("airquality:measurement:$gridKey")
                    ttl shouldBeGreaterThan 0L
                }

                mockWebServer.shutdown()
            }
        }
    }
}

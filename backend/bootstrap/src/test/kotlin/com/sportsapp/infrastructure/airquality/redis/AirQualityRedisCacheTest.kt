package com.sportsapp.infrastructure.airquality.redis

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.airquality.vo.AirQualityMeasurement
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class AirQualityRedisCacheTest(
    @Autowired private val airQualityRedisCache: AirQualityRedisCache,
    @Autowired private val stringRedisTemplate: StringRedisTemplate,
) : BaseIntegrationTest() {

    private val seoul = ZoneId.of("Asia/Seoul")

    init {
        Given("측정값을 캐시에 저장하면") {
            val gridKey = "37.567_126.978"
            val key = "airquality:measurement:$gridKey"
            stringRedisTemplate.unlink(key)
            val measurement = AirQualityMeasurement(
                pm10 = 42,
                pm25 = 18,
                stationName = "중구",
                measuredAt = ZonedDateTime.of(2026, 7, 4, 9, 15, 0, 0, seoul),
            )

            airQualityRedisCache.save(gridKey, measurement)

            When("TTL을 조회하면") {
                val ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS)

                Then("540~660초 범위(10분±jitter)이다") {
                    ttl shouldBeGreaterThan 539L
                    ttl shouldBeLessThan 661L
                }
            }

            When("findBy를 호출하면") {
                val result = airQualityRedisCache.findBy(gridKey)

                Then("저장한 값과 동일한 측정값이 복원된다") {
                    result.shouldNotBeNull()
                    result.pm10 shouldBe 42
                    result.pm25 shouldBe 18
                    result.stationName shouldBe "중구"
                    result.measuredAt.shouldNotBeNull()
                }
            }
        }

        Given("4필드 전부 null인 실패 측정값을 저장하면") {
            val gridKey = "37.500_127.000"
            val key = "airquality:measurement:$gridKey"
            stringRedisTemplate.unlink(key)

            airQualityRedisCache.save(gridKey, AirQualityMeasurement.empty())

            When("findBy를 호출하면") {
                val result = airQualityRedisCache.findBy(gridKey)

                Then("4필드 전부 null인 측정값이 그대로 복원된다") {
                    result.shouldNotBeNull()
                    result.pm10.shouldBeNull()
                    result.pm25.shouldBeNull()
                    result.stationName.shouldBeNull()
                    result.measuredAt.shouldBeNull()
                }
            }
        }

        Given("캐시가 존재하지 않을 때") {
            val gridKey = "0.000_0.000"
            val key = "airquality:measurement:$gridKey"
            stringRedisTemplate.unlink(key)

            When("findBy를 호출하면") {
                val result = airQualityRedisCache.findBy(gridKey)

                Then("null을 반환한다") {
                    result.shouldBeNull()
                }
            }
        }

        Given("동일 그리드키에 반복 저장하면") {
            val gridKey = "35.159_129.160"
            val key = "airquality:measurement:$gridKey"
            stringRedisTemplate.unlink(key)

            When("여러 번 save를 호출해도") {
                repeat(3) {
                    airQualityRedisCache.save(
                        gridKey,
                        AirQualityMeasurement(pm10 = 10 + it, pm25 = 5, stationName = "테스트", measuredAt = null),
                    )
                }

                Then("최신 값으로 수렴한다") {
                    val result = airQualityRedisCache.findBy(gridKey)
                    result.shouldNotBeNull()
                    result.pm10 shouldBe 12
                }
            }
        }
    }
}

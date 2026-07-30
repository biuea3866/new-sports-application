package com.sportsapp.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.application.dashboard.dto.DashboardSummaryResponse
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer

/**
 * Redis 캐시 값 직렬화 회귀 테스트.
 * 기본 GenericJackson2JsonRedisSerializer()는 Kotlin 모듈이 없어 data class 를
 * 역직렬화하지 못한다(no Creators). CacheConfig 가 구성하는 ObjectMapper 로는
 * data class 가 round-trip 되어야 한다.
 */
class CacheConfigTest : BehaviorSpec({

    // 프로덕션과 동일한 private buildRedisObjectMapper() 를 검증 대상으로 가져온다.
    val redisObjectMapper: ObjectMapper =
        CacheConfig::class.java
            .getDeclaredMethod("buildRedisObjectMapper")
            .apply { isAccessible = true }
            .invoke(CacheConfig()) as ObjectMapper

    val serializer = GenericJackson2JsonRedisSerializer(redisObjectMapper)

    Given("CacheConfig 의 Redis 값 직렬화기") {

        When("DashboardSummaryResponse 를 직렬화 후 역직렬화하면") {
            val original = DashboardSummaryResponse(
                facilities = DashboardSummaryResponse.FacilitiesSummary(count = 3, activeSlotsToday = 7),
                events = DashboardSummaryResponse.EventsSummary(
                    scheduled = 2,
                    open = 1,
                    closed = 4,
                    totalSeats = 100,
                    soldSeats = 80,
                ),
                products = DashboardSummaryResponse.ProductsSummary(active = 12, outOfStock = 2),
            )

            val restored = serializer.deserialize(serializer.serialize(original))

            Then("[U-01] 원본 data class 와 동일하게 복원된다") {
                restored shouldBe original
            }
        }

        When("nullable 필드가 null 인 data class 를 round-trip 하면") {
            val original = DashboardSummaryResponse(facilities = null, events = null, products = null)

            val restored = serializer.deserialize(serializer.serialize(original))

            Then("[U-02] null 필드가 보존된다") {
                restored shouldBe original
            }
        }
    }
})

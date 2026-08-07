package com.sportsapp.edgeapp.featureflag

import com.sportsapp.domain.common.FeatureContext
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations

/**
 * edge 자립 피처 플래그 평가기 (S2-12).
 *
 * edge 는 DataSource 가 없고 `svc_edge` 는 DB 권한이 0 이다. 공용 계약의 유일한 구현(platform 소유)은
 * `@PostConstruct` 에서 MySQL 을 조회하므로 그대로 쓰면 **부팅 단계에서 실패**한다. 그래서 Redis 만
 * 읽는 구현을 따로 둔다.
 *
 * 이 테스트가 고정하는 핵심은 **두 실패 상태를 구분**하는 것이다:
 *  - 키 없음(미생성·Redis 장애) → 호출부 `default`. 플래그 미생성이 **현재 정상 상태**다.
 *  - 미지원 전략 → **fail-closed(OFF)**. 정책 위반이 실제로 발생한 상황이라 켜진 채 오동작하는
 *    것보다 꺼지는 편이 안전하다.
 * 둘을 같게 처리하면 정책 위반이 "정상 기본값"에 묻힌다.
 */
class RedisOnlyFeatureFlagEvaluatorTest : DescribeSpec({

    fun evaluatorWith(snapshotByKey: Map<String, String?>): Pair<RedisOnlyFeatureFlagEvaluator, ValueOperations<String, String>> {
        val valueOperations = mockk<ValueOperations<String, String>>()
        val redisTemplate = mockk<StringRedisTemplate>()
        every { redisTemplate.opsForValue() } returns valueOperations
        snapshotByKey.forEach { (flagKey, snapshot) ->
            every { valueOperations.get("featureflag:flag:$flagKey") } returns snapshot
        }
        return RedisOnlyFeatureFlagEvaluator(redisTemplate) to valueOperations
    }

    fun globalToggle(key: String, enabled: Boolean, status: String = "ACTIVE") = """
        {"key":"$key","type":"OPERATIONAL","status":"$status",
         "strategy":{"strategyType":"GLOBAL_TOGGLE","enabled":$enabled},"description":"d"}
    """.trimIndent()

    val context = FeatureContext(userId = 1L)

    describe("GlobalToggle 해석") {
        it("enabled=true 면 true 를 반환한다") {
            val (evaluator, _) = evaluatorWith(mapOf("virtualqueue.enabled" to globalToggle("virtualqueue.enabled", true)))

            evaluator.isEnabled("virtualqueue.enabled", context, default = false) shouldBe true
        }

        it("enabled=false 면 false 를 반환한다 — 호출부 default 가 true 여도 스냅샷이 이긴다") {
            val (evaluator, _) = evaluatorWith(
                mapOf("virtualqueue.admission.enabled" to globalToggle("virtualqueue.admission.enabled", false)),
            )

            evaluator.isEnabled("virtualqueue.admission.enabled", context, default = true) shouldBe false
        }
    }

    describe("키 없음 — 호출부 default") {
        it("Redis 에 키가 없으면 default 를 반환한다 — 플래그 미생성이 현재 정상 상태다") {
            val (evaluator, _) = evaluatorWith(mapOf("virtualqueue.enabled" to null))

            // 대기열 플래그의 호출부 default 는 false(대기열 우회)라 폴백이 안전 방향이다.
            evaluator.isEnabled("virtualqueue.enabled", context, default = false) shouldBe false
            evaluator.isEnabled("virtualqueue.admission.enabled", context, default = true) shouldBe true
        }

        it("Redis 조회가 예외를 던져도 default 를 반환한다 — 장애가 대기열 폐쇄로 번지지 않는다") {
            val valueOperations = mockk<ValueOperations<String, String>>()
            val redisTemplate = mockk<StringRedisTemplate>()
            every { redisTemplate.opsForValue() } returns valueOperations
            every { valueOperations.get(any()) } throws RuntimeException("redis down")

            val evaluator = RedisOnlyFeatureFlagEvaluator(redisTemplate)

            evaluator.isEnabled("virtualqueue.enabled", context, default = false) shouldBe false
            evaluator.isEnabled("virtualqueue.admission.enabled", context, default = true) shouldBe true
        }

        it("스냅샷이 깨진 JSON 이어도 default 를 반환한다") {
            val (evaluator, _) = evaluatorWith(mapOf("virtualqueue.enabled" to "not-json"))

            evaluator.isEnabled("virtualqueue.enabled", context, default = true) shouldBe true
        }
    }

    describe("ARCHIVED — 호출부 default") {
        it("status=ARCHIVED 면 전략과 무관하게 default 를 반환한다 (platform 규칙과 동일)") {
            val (evaluator, _) = evaluatorWith(
                mapOf("virtualqueue.enabled" to globalToggle("virtualqueue.enabled", true, status = "ARCHIVED")),
            )

            evaluator.isEnabled("virtualqueue.enabled", context, default = false) shouldBe false
        }
    }

    describe("미지원 전략 — fail-closed") {
        listOf("PERCENTAGE_ROLLOUT", "ATTRIBUTE_MATCH", "VARIANT_BUCKETING").forEach { strategyType ->
            it("$strategyType 은 OFF 로 판정한다 — 호출부 default 가 true 여도 꺼진다") {
                // 키 없음(정상)과 달리 **정책 위반이 실제로 발생한 상황**이다. 켜진 채 잘못 평가되는
                // 것보다 꺼지는 편이 안전하고, 경고 로그로 즉시 드러난다.
                val snapshot = """
                    {"key":"virtualqueue.enabled","type":"OPERATIONAL","status":"ACTIVE",
                     "strategy":{"strategyType":"$strategyType","percentage":50},"description":"d"}
                """.trimIndent()
                val (evaluator, _) = evaluatorWith(mapOf("virtualqueue.enabled" to snapshot))

                evaluator.isEnabled("virtualqueue.enabled", context, default = true) shouldBe false
            }
        }
    }

    describe("L1 캐시") {
        it("같은 키를 연속 조회하면 Redis 를 한 번만 읽는다 — 요청마다 왕복하지 않는다") {
            val (evaluator, valueOperations) = evaluatorWith(
                mapOf("virtualqueue.enabled" to globalToggle("virtualqueue.enabled", true)),
            )

            repeat(5) { evaluator.isEnabled("virtualqueue.enabled", context, default = false) }

            verify(exactly = 1) { valueOperations.get("featureflag:flag:virtualqueue.enabled") }
        }

        it("서로 다른 키는 각각 조회한다") {
            val (evaluator, valueOperations) = evaluatorWith(
                mapOf(
                    "virtualqueue.enabled" to globalToggle("virtualqueue.enabled", true),
                    "virtualqueue.admission.enabled" to globalToggle("virtualqueue.admission.enabled", true),
                ),
            )

            evaluator.isEnabled("virtualqueue.enabled", context, default = false)
            evaluator.isEnabled("virtualqueue.admission.enabled", context, default = false)

            verify(exactly = 1) { valueOperations.get("featureflag:flag:virtualqueue.enabled") }
            verify(exactly = 1) { valueOperations.get("featureflag:flag:virtualqueue.admission.enabled") }
        }

        it("캐시 TTL 이 platform 재기록 주기보다 훨씬 짧다 — 플래그 변경이 사실상 즉시 반영된다") {
            // platform 의 LocalFeatureFlagStore 가 30초 주기로 Redis 를 재기록한다. L1 이 그보다 길면
            // 변경 반영이 캐시 만료까지 밀린다.
            RedisOnlyFeatureFlagEvaluator.LOCAL_CACHE_TTL_SECONDS shouldBe 3L
        }
    }

    describe("variant") {
        it("edge 는 variant 를 해석하지 않고 항상 default 를 반환한다 — GlobalToggle 만 지원한다") {
            val (evaluator, _) = evaluatorWith(
                mapOf("virtualqueue.enabled" to globalToggle("virtualqueue.enabled", true)),
            )

            evaluator.variant("virtualqueue.enabled", context, default = "control") shouldBe "control"
        }
    }
})

package com.sportsapp.contract

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import com.sportsapp.domain.featureflag.vo.FeatureFlagSnapshot
import com.sportsapp.infrastructure.featureflag.redis.FeatureFlagRedisKeys
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * 피처 플래그 Redis 스냅샷 소비자 계약 (S2-12).
 *
 * platform 이 `RedisFeatureFlagCacheStore` 로 쓴 JSON 을 edge 의 `RedisOnlyFeatureFlagEvaluator` 가
 * 읽는다. edge 는 platform 을 **의존하지 않으므로**(W1-06b 가 끊어 둔 결합) 스냅샷 타입을 공유할 수
 * 없고, 필드 이름·판별자 값을 리터럴로 들고 있다. platform 이 그중 하나만 바꿔도 컴파일은 통과하고
 * **런타임에 전 플래그가 기본값으로 떨어진다.** 그 드리프트를 머지 전에 잡는 것이 이 스펙이다.
 *
 * 계약 SSOT 는 `docs/feature-flag-redis-contract.md` 다.
 * `CrossServiceConsumerContractTest` 는 수정하지 않는다(같은 wave 파일 충돌 방지).
 */
class FeatureFlagSnapshotConsumerContractSpec : DescribeSpec({

    // platform 이 실제로 스냅샷을 직렬화할 때 쓰는 매퍼와 같은 구성을 쓴다.
    val objectMapper = ObjectMapper().findAndRegisterModules()

    fun snapshotJson(strategy: EvaluationStrategy, status: FeatureFlagStatus = FeatureFlagStatus.ACTIVE): String =
        objectMapper.writeValueAsString(
            FeatureFlagSnapshot(
                key = "virtualqueue.enabled",
                type = FeatureFlagType.OPERATIONAL,
                status = status,
                strategy = strategy,
                description = "대기열 활성화",
            ),
        )

    describe("edge 파서가 의존하는 필드가 스냅샷에 그대로 있다") {
        val json = snapshotJson(EvaluationStrategy.GlobalToggle(enabled = true))
        val parsed = objectMapper.readTree(json)

        it("status 필드가 최상위에 있고 ARCHIVED 판정에 쓸 수 있다") {
            parsed.has("status") shouldBe true
            parsed.path("status").asText() shouldBe "ACTIVE"
        }

        it("strategy 가 중첩 객체이고 판별자 필드 이름이 strategyType 이다") {
            parsed.path("strategy").isObject shouldBe true
            parsed.path("strategy").has("strategyType") shouldBe true
        }

        it("GlobalToggle 의 판별자 값이 GLOBAL_TOGGLE 이다 — edge 가 이 리터럴로 분기한다") {
            parsed.path("strategy").path("strategyType").asText() shouldBe "GLOBAL_TOGGLE"
        }

        it("GlobalToggle 의 on/off 필드 이름이 enabled 다") {
            parsed.path("strategy").path("enabled").asBoolean() shouldBe true
        }
    }

    describe("ARCHIVED 스냅샷도 같은 형태로 직렬화된다") {
        it("status 가 ARCHIVED 로 나간다 — edge 는 이 값을 보고 호출부 기본값으로 떨어진다") {
            val parsed = objectMapper.readTree(
                snapshotJson(EvaluationStrategy.GlobalToggle(enabled = true), status = FeatureFlagStatus.ARCHIVED),
            )

            parsed.path("status").asText() shouldBe "ARCHIVED"
        }
    }

    describe("캐시 키 계약") {
        it("키 접두사가 featureflag:flag: 다 — edge 가 같은 리터럴로 조회한다") {
            // edge 는 platform 을 의존할 수 없어 이 상수를 공유하지 못하고 리터럴을 들고 있다.
            FeatureFlagRedisKeys.cacheKey("virtualqueue.enabled") shouldContain "featureflag:flag:"
            FeatureFlagRedisKeys.cacheKey("virtualqueue.enabled") shouldBe "featureflag:flag:virtualqueue.enabled"
        }
    }

    describe("edge 가 해석하지 않는 전략도 판별자를 남긴다") {
        it("비-GlobalToggle 전략의 strategyType 이 GLOBAL_TOGGLE 이 아니다 — edge 의 fail-closed 판정 근거") {
            // 판별자가 없거나 같은 값으로 나가면 edge 가 미지원 전략을 GlobalToggle 로 오인해
            // enabled 없는 스냅샷을 false 로 읽는다(같은 OFF 지만 경고가 남지 않아 원인이 묻힌다).
            val parsed = objectMapper.readTree(snapshotJson(EvaluationStrategy.PercentageRollout(percentage = 50)))

            parsed.path("strategy").has("strategyType") shouldBe true
            (parsed.path("strategy").path("strategyType").asText() == "GLOBAL_TOGGLE") shouldBe false
        }
    }
})

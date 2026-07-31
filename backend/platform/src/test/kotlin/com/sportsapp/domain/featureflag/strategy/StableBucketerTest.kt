package com.sportsapp.domain.featureflag.strategy

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class StableBucketerTest : BehaviorSpec({

    Given("동일한 flagKey와 userId로 반복 호출하면") {
        val results = (1..20).map { StableBucketer.bucket("demo.feature.hello", 42L) }

        Then("항상 같은 버킷 값을 반환한다(sticky)") {
            results.toSet().size shouldBe 1
        }
    }

    Given("다양한 flagKey·userId 조합에 대해 bucket을 계산하면") {
        val buckets = (1L..500L).flatMap { userId ->
            listOf("flag.a", "flag.b", "flag.c").map { flagKey -> StableBucketer.bucket(flagKey, userId) }
        }

        Then("결과는 항상 0..99 범위를 벗어나지 않는다(음수 해시 floorMod 처리)") {
            buckets.forEach { bucket ->
                bucket shouldBeGreaterThanOrEqual 0
                bucket shouldBeLessThan 100
            }
        }
    }

    Given("서로 다른 flagKey에 같은 userId를 반복 적용하면") {
        val userIds = (1L..50L).toList()
        val bucketsForFlagA = userIds.map { StableBucketer.bucket("flag.rollout.a", it) }
        val bucketsForFlagB = userIds.map { StableBucketer.bucket("flag.rollout.b", it) }

        Then("두 flagKey의 버킷 분포는 독립적이다(salt 효과 — 전체 시퀀스가 동일하지 않다)") {
            bucketsForFlagA shouldNotBe bucketsForFlagB
        }
    }

    Given("표준 murmur3_32(seed=0) 참조 구현으로 산출한 입력 \"flag.test:1\"에 대해") {
        Then("StableBucketer는 참조 구현과 동일한 버킷 50을 반환한다") {
            // 참조값 산출: python mmh3.hash("flag.test:1", 0, signed=True) == 1284028050
            // 1284028050 % 100 == 50 (murmur3_32 표준 구현 대비 상수 오류를 검출하기 위한 회귀 벡터)
            StableBucketer.bucket("flag.test", 1L) shouldBe 50
        }
    }

    Given("표준 murmur3_32(seed=0) 참조 구현으로 산출한 입력 \"demo.feature.hello:42\"에 대해") {
        Then("StableBucketer는 참조 구현과 동일한 버킷 44를 반환한다") {
            // 참조값 산출: python mmh3.hash("demo.feature.hello:42", 0, signed=True) == 36476644
            // 36476644 % 100 == 44
            StableBucketer.bucket("demo.feature.hello", 42L) shouldBe 44
        }
    }
})

package com.sportsapp.domain.featuredemo.service

import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.featuredemo.exception.FeatureDisabledException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

private const val DEMO_FLAG_KEY = "demo.feature.hello"

class FeatureDemoDomainServiceTest : BehaviorSpec({

    val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()
    val featureDemoDomainService = FeatureDemoDomainService(featureFlagEvaluator)

    Given("demo.feature.hello 플래그가 존재하지 않는 상태") {
        every {
            featureFlagEvaluator.isEnabled(DEMO_FLAG_KEY, match { it.userId == 1L }, false)
        } returns false

        When("greet를 호출하면") {
            Then("기본값(다크) 판정으로 FeatureDisabledException을 던진다") {
                shouldThrow<FeatureDisabledException> {
                    featureDemoDomainService.greet(1L)
                }
            }
        }
    }

    Given("demo.feature.hello 플래그가 GlobalToggle ON 상태") {
        every {
            featureFlagEvaluator.isEnabled(DEMO_FLAG_KEY, match { it.userId == 2L }, false)
        } returns true

        When("greet를 호출하면") {
            val greeting = featureDemoDomainService.greet(2L)

            Then("인사 메시지가 담긴 Greeting을 반환한다") {
                greeting.flagKey shouldBe DEMO_FLAG_KEY
                greeting.message.isNotBlank() shouldBe true
            }
        }
    }

    Given("demo.feature.hello 플래그가 archive되어 평가기가 false를 반환하는 상태") {
        every {
            featureFlagEvaluator.isEnabled(DEMO_FLAG_KEY, match { it.userId == 3L }, false)
        } returns false

        When("greet를 호출하면") {
            Then("킬스위치가 즉시 적용되어 FeatureDisabledException을 던진다") {
                shouldThrow<FeatureDisabledException> {
                    featureDemoDomainService.greet(3L)
                }
            }
        }
    }

    Given("PercentageRollout 50%에서 포함으로 판정되는 userId") {
        val includedUserId = 100L
        every {
            featureFlagEvaluator.isEnabled(DEMO_FLAG_KEY, match { it.userId == includedUserId }, false)
        } returns true

        When("동일 X-User-Id로 greet를 반복 호출하면") {
            val firstCall = featureDemoDomainService.greet(includedUserId)
            val secondCall = featureDemoDomainService.greet(includedUserId)

            Then("두 호출 모두 성공(200 상당)으로 일관되게 판정된다") {
                firstCall.flagKey shouldBe DEMO_FLAG_KEY
                secondCall.flagKey shouldBe DEMO_FLAG_KEY
            }
        }
    }

    Given("PercentageRollout 50%에서 미포함으로 판정되는 userId") {
        val excludedUserId = 200L
        every {
            featureFlagEvaluator.isEnabled(DEMO_FLAG_KEY, match { it.userId == excludedUserId }, false)
        } returns false

        When("동일 X-User-Id로 greet를 반복 호출하면") {
            Then("두 호출 모두 실패(503 상당)로 일관되게 판정된다") {
                shouldThrow<FeatureDisabledException> {
                    featureDemoDomainService.greet(excludedUserId)
                }
                shouldThrow<FeatureDisabledException> {
                    featureDemoDomainService.greet(excludedUserId)
                }
            }
        }
    }

    Given("X-User-Id 없이 PercentageRollout 플래그를 호출하는 상황") {
        every {
            featureFlagEvaluator.isEnabled(DEMO_FLAG_KEY, FeatureContext.of(null), false)
        } returns false

        When("userId가 null인 채로 greet를 호출하면") {
            Then("기본값(false)으로 FeatureDisabledException을 던진다") {
                shouldThrow<FeatureDisabledException> {
                    featureDemoDomainService.greet(null)
                }
            }
        }
    }
})

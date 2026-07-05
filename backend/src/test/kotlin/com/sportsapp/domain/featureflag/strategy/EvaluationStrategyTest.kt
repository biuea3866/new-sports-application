package com.sportsapp.domain.featureflag.strategy

import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.exception.InvalidEvaluationStrategyException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class EvaluationStrategyTest : BehaviorSpec({

    Given("GlobalToggle(enabled=true)") {
        val strategy = EvaluationStrategy.GlobalToggle(enabled = true)

        Then("평가는 On을 반환한다") {
            strategy.evaluate("demo.feature.hello", FeatureContext.anonymous()) shouldBe FeatureEvaluation.On
        }
    }

    Given("GlobalToggle(enabled=false)") {
        val strategy = EvaluationStrategy.GlobalToggle(enabled = false)

        Then("평가는 Off를 반환한다") {
            strategy.evaluate("demo.feature.hello", FeatureContext.anonymous()) shouldBe FeatureEvaluation.Off
        }
    }

    Given("PercentageRollout(50) 전략에 동일한 userId를 반복 평가하면") {
        val strategy = EvaluationStrategy.PercentageRollout(percentage = 50)
        val context = FeatureContext.of(userId = 777L)
        val results = (1..10).map { strategy.evaluate("demo.feature.hello", context) }

        Then("항상 같은 판정을 받는다(sticky)") {
            results.toSet().size shouldBe 1
        }
    }

    Given("PercentageRollout 전략을 userId 없이 평가하면") {
        val strategy = EvaluationStrategy.PercentageRollout(percentage = 100)

        Then("Off를 반환한다") {
            strategy.evaluate("demo.feature.hello", FeatureContext.anonymous()) shouldBe FeatureEvaluation.Off
        }
    }

    Given("PercentageRollout(percentage=101)") {
        val strategy = EvaluationStrategy.PercentageRollout(percentage = 101)

        Then("validateFor 호출 시 InvalidEvaluationStrategyException을 던진다") {
            shouldThrow<InvalidEvaluationStrategyException> {
                strategy.validateFor(FeatureFlagType.RELEASE)
            }
        }
    }

    Given("AttributeMatch(plan, PREMIUM) 전략") {
        val strategy = EvaluationStrategy.AttributeMatch(attribute = "plan", value = "PREMIUM")

        Then("attributes에 plan=PREMIUM이면 On이다") {
            val context = FeatureContext(userId = null, attributes = mapOf("plan" to "PREMIUM"))
            strategy.evaluate("demo.feature.hello", context) shouldBe FeatureEvaluation.On
        }

        Then("attributes에 plan=BASIC이면 Off이다") {
            val context = FeatureContext(userId = null, attributes = mapOf("plan" to "BASIC"))
            strategy.evaluate("demo.feature.hello", context) shouldBe FeatureEvaluation.Off
        }

        Then("attributes에 plan 키가 없으면 Off이다") {
            strategy.evaluate("demo.feature.hello", FeatureContext.anonymous()) shouldBe FeatureEvaluation.Off
        }
    }

    Given("VariantBucketing([A:50, B:50]) 전략에 동일한 userId를 반복 평가하면") {
        val strategy = EvaluationStrategy.VariantBucketing(
            variants = listOf(Variant("A", 50), Variant("B", 50)),
        )
        val context = FeatureContext.of(userId = 999L)
        val results = (1..10).map { strategy.evaluate("demo.feature.experiment", context) }

        Then("항상 동일한 variant(Assigned)를 받는다") {
            results.toSet().size shouldBe 1
            (results.first() as FeatureEvaluation.Assigned).variantName shouldBe (results.last() as FeatureEvaluation.Assigned).variantName
        }
    }

    Given("VariantBucketing 전략을 userId 없이 평가하면") {
        val strategy = EvaluationStrategy.VariantBucketing(
            variants = listOf(Variant("A", 50), Variant("B", 50)),
        )

        Then("Off를 반환한다") {
            strategy.evaluate("demo.feature.experiment", FeatureContext.anonymous()) shouldBe FeatureEvaluation.Off
        }
    }

    Given("VariantBucketing 전략을 EXPERIMENT 타입에 대해 검증하면") {
        val strategy = EvaluationStrategy.VariantBucketing(
            variants = listOf(Variant("A", 50), Variant("B", 50)),
        )

        Then("예외 없이 통과한다") {
            shouldNotThrowAny { strategy.validateFor(FeatureFlagType.EXPERIMENT) }
        }
    }

    Given("VariantBucketing 전략을 EXPERIMENT가 아닌 타입에 대해 검증하면") {
        val strategy = EvaluationStrategy.VariantBucketing(
            variants = listOf(Variant("A", 50), Variant("B", 50)),
        )

        Then("InvalidEvaluationStrategyException을 던진다") {
            shouldThrow<InvalidEvaluationStrategyException> {
                strategy.validateFor(FeatureFlagType.RELEASE)
            }
        }
    }

    Given("VariantBucketing 전략의 variant가 4개를 초과하면") {
        val strategy = EvaluationStrategy.VariantBucketing(
            variants = listOf(Variant("A", 20), Variant("B", 20), Variant("C", 20), Variant("D", 20), Variant("E", 20)),
        )

        Then("validateFor 호출 시 InvalidEvaluationStrategyException을 던진다") {
            shouldThrow<InvalidEvaluationStrategyException> {
                strategy.validateFor(FeatureFlagType.EXPERIMENT)
            }
        }
    }

    Given("VariantBucketing 전략의 weight 합이 100이 아니면") {
        val strategy = EvaluationStrategy.VariantBucketing(
            variants = listOf(Variant("A", 30), Variant("B", 30)),
        )

        Then("validateFor 호출 시 InvalidEvaluationStrategyException을 던진다") {
            shouldThrow<InvalidEvaluationStrategyException> {
                strategy.validateFor(FeatureFlagType.EXPERIMENT)
            }
        }
    }
})

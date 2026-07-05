package com.sportsapp.domain.featureflag.entity

import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.featureflag.event.FeatureFlagChangedEvent
import com.sportsapp.domain.featureflag.exception.FeatureFlagStatusConflictException
import com.sportsapp.domain.featureflag.exception.InvalidEvaluationStrategyException
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import com.sportsapp.domain.featureflag.strategy.FeatureEvaluation
import com.sportsapp.domain.featureflag.strategy.Variant
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class FeatureFlagTest : BehaviorSpec({

    fun createFlag(
        flagKey: String = "demo.feature.hello",
        type: FeatureFlagType = FeatureFlagType.RELEASE,
        strategy: EvaluationStrategy = EvaluationStrategy.GlobalToggle(enabled = true),
        description: String? = "demo flag",
    ): FeatureFlag = FeatureFlag.create(
        flagKey = flagKey,
        type = type,
        strategy = strategy,
        description = description,
    )

    Given("유효한 값으로 FeatureFlag를 생성하면") {
        val flag = createFlag()

        Then("status는 ACTIVE로 생성된다") {
            flag.status shouldBe FeatureFlagStatus.ACTIVE
        }

        Then("입력한 전략·설명이 그대로 저장된다") {
            flag.strategy shouldBe EvaluationStrategy.GlobalToggle(enabled = true)
            flag.description shouldBe "demo flag"
        }

        Then("FeatureFlagChangedEvent가 domainEvents에 적재된다") {
            val flagWithEvent = createFlag()
            val events = flagWithEvent.pullDomainEvents()
            events shouldHaveSize 1
            val changedEvent = events.single()
            changedEvent.shouldBeInstanceOf<FeatureFlagChangedEvent>()
            changedEvent.flagKey shouldBe flagWithEvent.flagKey
        }
    }

    Given("형식에 맞지 않는 flagKey(대문자 포함)로 생성하면") {
        Then("IllegalArgumentException이 발생한다") {
            shouldThrow<IllegalArgumentException> {
                createFlag(flagKey = "Demo.Feature.Hello")
            }
        }
    }

    Given("percentage가 100을 초과하는 PercentageRollout으로 생성하면") {
        Then("InvalidEvaluationStrategyException이 발생한다") {
            shouldThrow<InvalidEvaluationStrategyException> {
                createFlag(strategy = EvaluationStrategy.PercentageRollout(percentage = 101))
            }
        }
    }

    Given("variant가 4개를 초과하는 VariantBucketing으로 EXPERIMENT 플래그를 생성하면") {
        Then("InvalidEvaluationStrategyException이 발생한다") {
            shouldThrow<InvalidEvaluationStrategyException> {
                createFlag(
                    type = FeatureFlagType.EXPERIMENT,
                    strategy = EvaluationStrategy.VariantBucketing(
                        variants = listOf(
                            Variant("A", 20),
                            Variant("B", 20),
                            Variant("C", 20),
                            Variant("D", 20),
                            Variant("E", 20),
                        ),
                    ),
                )
            }
        }
    }

    Given("weight 합이 100이 아닌 VariantBucketing으로 EXPERIMENT 플래그를 생성하면") {
        Then("InvalidEvaluationStrategyException이 발생한다") {
            shouldThrow<InvalidEvaluationStrategyException> {
                createFlag(
                    type = FeatureFlagType.EXPERIMENT,
                    strategy = EvaluationStrategy.VariantBucketing(
                        variants = listOf(Variant("A", 40), Variant("B", 40)),
                    ),
                )
            }
        }
    }

    Given("EXPERIMENT 타입이 아닌데 VariantBucketing 전략으로 생성하면") {
        Then("InvalidEvaluationStrategyException이 발생한다") {
            shouldThrow<InvalidEvaluationStrategyException> {
                createFlag(
                    type = FeatureFlagType.RELEASE,
                    strategy = EvaluationStrategy.VariantBucketing(
                        variants = listOf(Variant("A", 50), Variant("B", 50)),
                    ),
                )
            }
        }
    }

    Given("ACTIVE 상태의 FeatureFlag를 evaluate하면") {
        val flag = createFlag(strategy = EvaluationStrategy.GlobalToggle(enabled = true))

        Then("전략 평가 결과를 그대로 반환한다") {
            flag.evaluate(FeatureContext.anonymous()) shouldBe FeatureEvaluation.On
        }
    }

    Given("ARCHIVED 상태의 FeatureFlag를 evaluate하면") {
        val flag = createFlag(strategy = EvaluationStrategy.GlobalToggle(enabled = true))
        flag.archive()

        Then("전략 평가를 수행하지 않고 Off를 반환한다(평가 제외 신호)") {
            flag.evaluate(FeatureContext.anonymous()) shouldBe FeatureEvaluation.Off
        }
    }

    Given("ACTIVE 상태의 FeatureFlag에 archive()를 호출하면") {
        val flag = createFlag()
        flag.archive()

        Then("status가 ARCHIVED로 전이된다") {
            flag.status shouldBe FeatureFlagStatus.ARCHIVED
        }

        Then("FeatureFlagChangedEvent가 domainEvents에 적재된다") {
            val flagWithEvent = createFlag()
            flagWithEvent.pullDomainEvents()
            flagWithEvent.archive()
            val events = flagWithEvent.pullDomainEvents()
            events shouldHaveSize 1
            val changedEvent = events.single()
            changedEvent.shouldBeInstanceOf<FeatureFlagChangedEvent>()
            changedEvent.flagKey shouldBe flagWithEvent.flagKey
        }
    }

    Given("이미 ARCHIVED 상태인 FeatureFlag에 archive()를 재호출하면") {
        val flag = createFlag()
        flag.archive()

        Then("FeatureFlagStatusConflictException이 발생한다") {
            shouldThrow<FeatureFlagStatusConflictException> {
                flag.archive()
            }
        }
    }

    Given("ARCHIVED 상태의 FeatureFlag에 activate()를 호출하면") {
        val flag = createFlag()
        flag.archive()
        flag.activate()

        Then("status가 ACTIVE로 전이된다") {
            flag.status shouldBe FeatureFlagStatus.ACTIVE
        }
    }

    Given("이미 ACTIVE 상태인 FeatureFlag에 activate()를 재호출하면") {
        val flag = createFlag()

        Then("FeatureFlagStatusConflictException이 발생한다") {
            shouldThrow<FeatureFlagStatusConflictException> {
                flag.activate()
            }
        }
    }

    Given("ACTIVE 상태의 FeatureFlag에 updateStrategy()를 호출하면") {
        val flag = createFlag()
        flag.updateStrategy(EvaluationStrategy.GlobalToggle(enabled = false), "updated")

        Then("전략과 설명이 갱신된다") {
            flag.strategy shouldBe EvaluationStrategy.GlobalToggle(enabled = false)
            flag.description shouldBe "updated"
        }

        Then("FeatureFlagChangedEvent가 domainEvents에 적재된다") {
            val flagWithEvent = createFlag()
            flagWithEvent.pullDomainEvents()
            flagWithEvent.updateStrategy(EvaluationStrategy.GlobalToggle(enabled = false), "updated")
            flagWithEvent.pullDomainEvents() shouldHaveSize 1
        }
    }

    Given("ARCHIVED 상태의 FeatureFlag에 updateStrategy()를 호출하면") {
        val flag = createFlag()
        flag.archive()

        Then("FeatureFlagStatusConflictException이 발생한다") {
            shouldThrow<FeatureFlagStatusConflictException> {
                flag.updateStrategy(EvaluationStrategy.GlobalToggle(enabled = false), "updated")
            }
        }
    }

    Given("유효하지 않은 전략으로 updateStrategy()를 호출하면") {
        val flag = createFlag()

        Then("InvalidEvaluationStrategyException이 발생한다") {
            shouldThrow<InvalidEvaluationStrategyException> {
                flag.updateStrategy(EvaluationStrategy.PercentageRollout(percentage = 200), "updated")
            }
        }
    }

    Given("FeatureFlag의 toSnapshot을 호출하면") {
        val flag = createFlag()

        Then("현재 key·type·status·strategy·description을 담은 스냅샷을 반환한다") {
            val snapshot = flag.toSnapshot()
            snapshot.key shouldBe flag.flagKey
            snapshot.type shouldBe flag.type
            snapshot.status shouldBe flag.status
            snapshot.strategy shouldBe flag.strategy
            snapshot.description shouldBe flag.description
        }
    }
})

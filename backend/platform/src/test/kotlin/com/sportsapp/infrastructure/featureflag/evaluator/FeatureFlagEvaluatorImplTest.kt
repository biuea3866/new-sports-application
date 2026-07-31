package com.sportsapp.infrastructure.featureflag.evaluator

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import com.sportsapp.domain.featureflag.strategy.FeatureEvaluation
import com.sportsapp.domain.featureflag.strategy.Variant
import com.sportsapp.domain.featureflag.vo.FeatureFlagSnapshot
import com.sportsapp.infrastructure.featureflag.local.LocalFeatureFlagStore
import com.sportsapp.infrastructure.featureflag.metrics.FeatureFlagEvaluationMetrics
import com.sportsapp.infrastructure.featureflag.sampleFeatureFlagSnapshot
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessResourceFailureException

/**
 * `FeatureFlagEvaluatorImpl` — 공용 평가 클라이언트 폴백 체인(FR-11) + 노출 집계(FR-13) 검증
 * (BE-06 티켓 테스트 케이스 8종).
 */
class FeatureFlagEvaluatorImplTest : BehaviorSpec({

    Given("로컬 스냅샷에 GlobalToggle ON 플래그가 있을 때") {
        val localFeatureFlagStore = mockk<LocalFeatureFlagStore>()
        val snapshot = sampleFeatureFlagSnapshot(key = "demo.feature.global-on", enabled = true)
        every { localFeatureFlagStore.get(snapshot.key) } returns snapshot
        val evaluator = FeatureFlagEvaluatorImpl(localFeatureFlagStore, SimpleMeterRegistry())

        When("isEnabled를 호출하면") {
            val result = evaluator.isEnabled(snapshot.key, FeatureContext.anonymous(), default = false)

            Then("true를 반환하고 로컬 히트만으로 처리되어 refresh는 호출되지 않는다") {
                result shouldBe true
                verify(exactly = 0) { localFeatureFlagStore.refresh(any()) }
            }
        }
    }

    Given("정의되지 않은 key를 평가할 때") {
        val localFeatureFlagStore = mockk<LocalFeatureFlagStore>()
        val undefinedKey = "demo.feature.undefined"
        every { localFeatureFlagStore.get(undefinedKey) } returns null
        every { localFeatureFlagStore.refresh(undefinedKey) } just Runs
        val evaluator = FeatureFlagEvaluatorImpl(localFeatureFlagStore, SimpleMeterRegistry())

        When("isEnabled를 호출하면") {
            val result = evaluator.isEnabled(undefinedKey, FeatureContext.anonymous(), default = true)

            Then("호출부 default를 반환하고 refresh로 재조회를 시도한다") {
                result shouldBe true
                verify(exactly = 1) { localFeatureFlagStore.refresh(undefinedKey) }
            }
        }

        When("variant를 호출하면") {
            val result = evaluator.variant(undefinedKey, FeatureContext.anonymous(), default = "off")

            Then("호출부 default를 반환한다") {
                result shouldBe "off"
            }
        }
    }

    Given("ARCHIVED 상태의 플래그를 평가할 때") {
        val localFeatureFlagStore = mockk<LocalFeatureFlagStore>()
        val archivedSnapshot = sampleFeatureFlagSnapshot(key = "demo.feature.archived", enabled = true)
            .copy(status = FeatureFlagStatus.ARCHIVED)
        every { localFeatureFlagStore.get(archivedSnapshot.key) } returns archivedSnapshot
        val evaluator = FeatureFlagEvaluatorImpl(localFeatureFlagStore, SimpleMeterRegistry())

        When("isEnabled를 호출하면") {
            val result = evaluator.isEnabled(archivedSnapshot.key, FeatureContext.anonymous(), default = true)

            Then("평가 대상에서 제외되어 호출부 default를 반환한다") {
                result shouldBe true
                verify(exactly = 0) { localFeatureFlagStore.refresh(any()) }
            }
        }
    }

    Given("Redis·MySQL이 모두 다운이지만 로컬에 마지막 성공 스냅샷이 남아있을 때") {
        val localFeatureFlagStore = mockk<LocalFeatureFlagStore>()
        val lastKnownGoodSnapshot = sampleFeatureFlagSnapshot(key = "demo.feature.last-known-good", enabled = true)
        every { localFeatureFlagStore.get(lastKnownGoodSnapshot.key) } returns lastKnownGoodSnapshot
        every { localFeatureFlagStore.refresh(any()) } throws DataAccessResourceFailureException("redis+mysql down")
        val evaluator = FeatureFlagEvaluatorImpl(localFeatureFlagStore, SimpleMeterRegistry())

        When("isEnabled를 호출하면") {
            val result = shouldNotThrowAny {
                evaluator.isEnabled(lastKnownGoodSnapshot.key, FeatureContext.anonymous(), default = false)
            }

            Then("로컬의 마지막 성공 스냅샷 값으로 평가하고 refresh는 호출되지 않는다(예외 미전파)") {
                result shouldBe true
                verify(exactly = 0) { localFeatureFlagStore.refresh(any()) }
            }
        }
    }

    Given("로컬·Redis·MySQL 모두에 값이 없고 재조회도 실패하는 상태에서") {
        val localFeatureFlagStore = mockk<LocalFeatureFlagStore>()
        val missingKey = "demo.feature.missing-everywhere"
        every { localFeatureFlagStore.get(missingKey) } returns null
        every { localFeatureFlagStore.refresh(missingKey) } throws DataAccessResourceFailureException("mysql down")
        val evaluator = FeatureFlagEvaluatorImpl(localFeatureFlagStore, SimpleMeterRegistry())

        val listAppender = ListAppender<ILoggingEvent>().apply { start() }
        val logger = LoggerFactory.getLogger(FeatureFlagEvaluatorImpl::class.java) as Logger
        logger.addAppender(listAppender)

        When("isEnabled를 호출하면") {
            val result = shouldNotThrowAny {
                evaluator.isEnabled(missingKey, FeatureContext.anonymous(), default = true)
            }

            Then("예외를 전파하지 않고 호출부 default를 반환한다") {
                result shouldBe true
            }

            Then("redis-degraded 경보(WARN, source=feature-flag)가 발신된다") {
                val degradedLog = requireNotNull(
                    listAppender.list.find { it.formattedMessage.contains("redis-degraded") },
                ) { "redis-degraded 로그가 발신되지 않았다" }

                degradedLog.level shouldBe Level.WARN
                degradedLog.formattedMessage.contains("source=feature-flag") shouldBe true
            }
        }

        logger.detachAppender(listAppender)
    }

    Given("PercentageRollout 50% 플래그를 동일 userId로 반복 평가하면") {
        val localFeatureFlagStore = mockk<LocalFeatureFlagStore>()
        val rolloutSnapshot = FeatureFlagSnapshot(
            key = "demo.feature.rollout",
            type = FeatureFlagType.OPERATIONAL,
            status = FeatureFlagStatus.ACTIVE,
            strategy = EvaluationStrategy.PercentageRollout(percentage = 50),
            description = null,
        )
        every { localFeatureFlagStore.get(rolloutSnapshot.key) } returns rolloutSnapshot
        val evaluator = FeatureFlagEvaluatorImpl(localFeatureFlagStore, SimpleMeterRegistry())
        val context = FeatureContext.of(userId = 42L)

        When("동일 context로 5회 반복 평가하면") {
            val results = (1..5).map { evaluator.isEnabled(rolloutSnapshot.key, context, default = false) }

            Then("모든 결과가 동일하다(sticky)") {
                results.toSet().size shouldBe 1
            }
        }
    }

    Given("EXPERIMENT VariantBucketing 플래그를 평가할 때") {
        val localFeatureFlagStore = mockk<LocalFeatureFlagStore>()
        val strategy = EvaluationStrategy.VariantBucketing(
            variants = listOf(Variant(name = "A", weight = 50), Variant(name = "B", weight = 50)),
        )
        val variantSnapshot = FeatureFlagSnapshot(
            key = "demo.feature.variant",
            type = FeatureFlagType.EXPERIMENT,
            status = FeatureFlagStatus.ACTIVE,
            strategy = strategy,
            description = null,
        )
        val context = FeatureContext.of(userId = 777L)
        val expectedAssignment = strategy.evaluate(variantSnapshot.key, context) as FeatureEvaluation.Assigned
        every { localFeatureFlagStore.get(variantSnapshot.key) } returns variantSnapshot
        val evaluator = FeatureFlagEvaluatorImpl(localFeatureFlagStore, SimpleMeterRegistry())

        When("variant를 호출하면") {
            val result = evaluator.variant(variantSnapshot.key, context, default = "control")

            Then("VariantBucketing이 배정한 이름을 반환한다") {
                result shouldBe expectedAssignment.variantName
            }
        }
    }

    Given("variant 평가 결과가 Assigned가 아니거나 key가 존재하지 않을 때") {
        val localFeatureFlagStore = mockk<LocalFeatureFlagStore>()
        val toggleSnapshot = sampleFeatureFlagSnapshot(key = "demo.feature.toggle-variant", enabled = true)
        val undefinedKey = "demo.feature.variant-undefined"
        every { localFeatureFlagStore.get(toggleSnapshot.key) } returns toggleSnapshot
        every { localFeatureFlagStore.get(undefinedKey) } returns null
        every { localFeatureFlagStore.refresh(undefinedKey) } just Runs
        val evaluator = FeatureFlagEvaluatorImpl(localFeatureFlagStore, SimpleMeterRegistry())

        When("GlobalToggle(On) 플래그에 variant를 호출하면") {
            val result = evaluator.variant(toggleSnapshot.key, FeatureContext.anonymous(), default = "control")

            Then("Assigned가 아니므로 호출부 default를 반환한다") {
                result shouldBe "control"
            }
        }

        When("존재하지 않는 key에 variant를 호출하면") {
            val result = evaluator.variant(undefinedKey, FeatureContext.anonymous(), default = "control")

            Then("호출부 default를 반환한다") {
                result shouldBe "control"
            }
        }
    }

    Given("평가 요청이 발생하면") {
        val localFeatureFlagStore = mockk<LocalFeatureFlagStore>()
        val meterRegistry = SimpleMeterRegistry()
        val snapshot = sampleFeatureFlagSnapshot(key = "demo.feature.counter", enabled = true)
        every { localFeatureFlagStore.get(snapshot.key) } returns snapshot
        val evaluator = FeatureFlagEvaluatorImpl(localFeatureFlagStore, meterRegistry)

        When("isEnabled를 2회 호출하면") {
            evaluator.isEnabled(snapshot.key, FeatureContext.anonymous(), default = false)
            evaluator.isEnabled(snapshot.key, FeatureContext.anonymous(), default = false)

            Then("평가 1회당 노출 카운터가 1씩 증가해 총 2가 된다") {
                meterRegistry.counter(
                    FeatureFlagEvaluationMetrics.EVALUATIONS_COUNTER,
                    "key",
                    snapshot.key,
                ).count() shouldBe 2.0
            }
        }
    }
})

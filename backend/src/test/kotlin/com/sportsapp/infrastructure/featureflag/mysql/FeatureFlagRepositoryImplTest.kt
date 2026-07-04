package com.sportsapp.infrastructure.featureflag.mysql

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.featureflag.entity.FeatureFlag
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.repository.FeatureFlagRepository
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import com.sportsapp.domain.featureflag.strategy.Variant
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

class FeatureFlagRepositoryImplTest(
    @Autowired private val featureFlagRepository: FeatureFlagRepository,
) : BaseJpaIntegrationTest() {

    init {
        Given("GlobalToggle 전략의 신규 피처 플래그를 저장한 상황") {
            val flagKey = "demo.feature.global-toggle-${System.nanoTime()}"
            val created = FeatureFlag.create(
                flagKey = flagKey,
                type = FeatureFlagType.RELEASE,
                strategy = EvaluationStrategy.GlobalToggle(enabled = true),
                description = "글로벌 토글 플래그",
            )
            val saved = featureFlagRepository.save(created)

            When("findByKey로 동일 키를 조회하면") {
                val found = featureFlagRepository.findByKey(flagKey)

                Then("저장한 플래그가 필드 손실 없이 반환된다") {
                    found.shouldNotBeNull()
                    found.id shouldBe saved.id
                    found.flagKey shouldBe flagKey
                    found.type shouldBe FeatureFlagType.RELEASE
                    found.status shouldBe FeatureFlagStatus.ACTIVE
                    found.strategy shouldBe EvaluationStrategy.GlobalToggle(enabled = true)
                    found.description shouldBe "글로벌 토글 플래그"
                }
            }
        }

        Given("ACTIVE 플래그 1건과 ARCHIVED 플래그 1건이 저장된 상황") {
            val activeKey = "demo.feature.active-${System.nanoTime()}"
            val archivedKey = "demo.feature.archived-${System.nanoTime()}"

            featureFlagRepository.save(
                FeatureFlag.create(
                    flagKey = activeKey,
                    type = FeatureFlagType.OPERATIONAL,
                    strategy = EvaluationStrategy.GlobalToggle(enabled = true),
                    description = null,
                ),
            )
            val archived = FeatureFlag.create(
                flagKey = archivedKey,
                type = FeatureFlagType.OPERATIONAL,
                strategy = EvaluationStrategy.GlobalToggle(enabled = false),
                description = null,
            )
            archived.archive()
            featureFlagRepository.save(archived)

            When("findAllActive를 호출하면") {
                val result = featureFlagRepository.findAllActive()
                val resultKeys = result.map { it.flagKey }

                Then("ARCHIVED 플래그는 제외하고 ACTIVE 플래그만 반환한다") {
                    resultKeys shouldContain activeKey
                    resultKeys shouldNotContain archivedKey
                    result.all { it.status == FeatureFlagStatus.ACTIVE } shouldBe true
                }
            }
        }

        Given("RELEASE 타입 플래그 2건과 EXPERIMENT 타입 플래그 1건이 저장된 상황") {
            val releaseKeyA = "demo.feature.release-a-${System.nanoTime()}"
            val releaseKeyB = "demo.feature.release-b-${System.nanoTime()}"
            val experimentKey = "demo.feature.experiment-${System.nanoTime()}"

            listOf(releaseKeyA, releaseKeyB).forEach { key ->
                featureFlagRepository.save(
                    FeatureFlag.create(
                        flagKey = key,
                        type = FeatureFlagType.RELEASE,
                        strategy = EvaluationStrategy.GlobalToggle(enabled = true),
                        description = null,
                    ),
                )
            }
            featureFlagRepository.save(
                FeatureFlag.create(
                    flagKey = experimentKey,
                    type = FeatureFlagType.EXPERIMENT,
                    strategy = EvaluationStrategy.VariantBucketing(
                        variants = listOf(Variant(name = "A", weight = 100)),
                    ),
                    description = null,
                ),
            )

            When("findAll(status=null, type=RELEASE)로 조회하면") {
                val result = featureFlagRepository.findAll(status = null, type = FeatureFlagType.RELEASE)
                val resultKeys = result.map { it.flagKey }

                Then("타입 필터만 적용되어 RELEASE 플래그만 반환하고 EXPERIMENT 플래그는 제외한다") {
                    resultKeys shouldContainAll listOf(releaseKeyA, releaseKeyB)
                    resultKeys shouldNotContain experimentKey
                    result.all { it.type == FeatureFlagType.RELEASE } shouldBe true
                }
            }
        }

        Given("어떤 플래그도 저장되지 않은 키") {
            val missingKey = "demo.feature.missing-${System.nanoTime()}"

            When("findByKey로 조회하면") {
                val found = featureFlagRepository.findByKey(missingKey)

                Then("null이 반환된다 (Optional 누출 없음)") {
                    found.shouldBeNull()
                }
            }
        }

        Given("중복 검증용 플래그가 저장된 상황") {
            val existingKey = "demo.feature.exists-${System.nanoTime()}"
            val missingKey = "demo.feature.not-exists-${System.nanoTime()}"
            featureFlagRepository.save(
                FeatureFlag.create(
                    flagKey = existingKey,
                    type = FeatureFlagType.RELEASE,
                    strategy = EvaluationStrategy.GlobalToggle(enabled = true),
                    description = null,
                ),
            )

            When("existsByKey를 각각 호출하면") {
                val existsResult = featureFlagRepository.existsByKey(existingKey)
                val notExistsResult = featureFlagRepository.existsByKey(missingKey)

                Then("존재하는 키는 true, 없는 키는 false를 반환한다") {
                    existsResult shouldBe true
                    notExistsResult shouldBe false
                }
            }
        }

        Given("EXPERIMENT 타입에 VariantBucketing 전략을 가진 플래그를 저장한 상황") {
            val flagKey = "demo.feature.variant-bucketing-${System.nanoTime()}"
            val variants = listOf(
                Variant(name = "control", weight = 50),
                Variant(name = "treatment", weight = 50),
            )
            featureFlagRepository.save(
                FeatureFlag.create(
                    flagKey = flagKey,
                    type = FeatureFlagType.EXPERIMENT,
                    strategy = EvaluationStrategy.VariantBucketing(variants = variants),
                    description = null,
                ),
            )

            When("findByKey로 다시 조회하면") {
                val found = featureFlagRepository.findByKey(flagKey)

                Then("variants 리스트가 손실 없이 복원된다") {
                    found.shouldNotBeNull()
                    val restoredStrategy = found.strategy as EvaluationStrategy.VariantBucketing
                    restoredStrategy.variants shouldHaveSize 2
                    restoredStrategy.variants shouldContainExactlyInAnyOrder variants
                }
            }
        }
    }
}

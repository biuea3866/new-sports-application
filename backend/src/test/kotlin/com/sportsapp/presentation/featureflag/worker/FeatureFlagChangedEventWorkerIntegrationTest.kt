package com.sportsapp.presentation.featureflag.worker

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.featureflag.usecase.PropagateFeatureFlagChangeUseCase
import com.sportsapp.domain.featureflag.dto.ArchiveFeatureFlagCommand
import com.sportsapp.domain.featureflag.dto.CreateFeatureFlagCommand
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.gateway.FeatureFlagCacheStore
import com.sportsapp.domain.featureflag.repository.FeatureFlagRepository
import com.sportsapp.domain.featureflag.service.FeatureFlagDomainService
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.spyk
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionTemplate

/**
 * 실제 빈으로 등록된 [PropagateFeatureFlagChangeUseCase]를 MockK `spyk`로 감싸 `@Primary`로
 * 대체한다 — 위임 동작은 그대로 유지하면서 호출 여부·횟수를 관찰하기 위함
 * (레포에 SpringMockK 의존성이 없어 `@Bean`+`@Primary` 수동 오버라이드 방식을 사용한다).
 */
@TestConfiguration
class FeatureFlagChangedEventWorkerTestConfig {

    @Bean
    @Primary
    fun spyPropagateFeatureFlagChangeUseCase(
        featureFlagDomainService: FeatureFlagDomainService,
    ): PropagateFeatureFlagChangeUseCase = spyk(PropagateFeatureFlagChangeUseCase(featureFlagDomainService))
}

/**
 * `FeatureFlagChangedEventWorker`가 `@TransactionalEventListener(AFTER_COMMIT)`으로
 * 실제 트랜잭션 커밋/롤백 경로에서 올바르게 동작하는지 Testcontainers(MySQL·Redis)로 검증한다 (BE-07).
 */
@Import(FeatureFlagChangedEventWorkerTestConfig::class)
class FeatureFlagChangedEventWorkerIntegrationTest(
    @Autowired private val featureFlagDomainService: FeatureFlagDomainService,
    @Autowired private val featureFlagCacheStore: FeatureFlagCacheStore,
    @Autowired private val featureFlagRepository: FeatureFlagRepository,
    @Autowired private val propagateFeatureFlagChangeUseCase: PropagateFeatureFlagChangeUseCase,
    @Autowired private val transactionTemplate: TransactionTemplate,
) : BaseIntegrationTest() {

    init {
        Given("피처 플래그 생성 트랜잭션이 커밋되는 상황") {
            val flagKey = "demo.feature.propagation-commit"
            val command = CreateFeatureFlagCommand(
                flagKey = flagKey,
                type = FeatureFlagType.RELEASE,
                strategy = EvaluationStrategy.GlobalToggle(enabled = true),
                description = "commit propagation test",
                actorUserId = 1L,
            )

            When("트랜잭션을 커밋하면") {
                transactionTemplate.execute { featureFlagDomainService.create(command) }

                Then("FeatureFlagChangedEventWorker가 PropagateFeatureFlagChangeUseCase.execute를 호출하고 캐시가 갱신된다") {
                    verify(exactly = 1) { propagateFeatureFlagChangeUseCase.execute(flagKey) }
                    val snapshot = featureFlagCacheStore.get(flagKey)
                    snapshot.shouldNotBeNull()
                    snapshot.status shouldBe FeatureFlagStatus.ACTIVE
                }
            }
        }

        Given("피처 플래그 생성 트랜잭션이 롤백되는 상황") {
            val flagKey = "demo.feature.propagation-rollback"
            val command = CreateFeatureFlagCommand(
                flagKey = flagKey,
                type = FeatureFlagType.RELEASE,
                strategy = EvaluationStrategy.GlobalToggle(enabled = true),
                description = "rollback propagation test",
                actorUserId = 1L,
            )

            When("트랜잭션 도중 예외가 발생해 롤백되면") {
                val result = runCatching {
                    transactionTemplate.execute {
                        featureFlagDomainService.create(command)
                        throw IllegalStateException("강제 롤백")
                    }
                }

                Then("리스너가 전파를 트리거하지 않는다(AFTER_COMMIT)") {
                    result.isFailure shouldBe true
                    verify(exactly = 0) { propagateFeatureFlagChangeUseCase.execute(flagKey) }
                    featureFlagRepository.findByKey(flagKey).shouldBeNull()
                    featureFlagCacheStore.get(flagKey).shouldBeNull()
                }
            }
        }

        Given("이미 존재하는 ACTIVE 플래그를 archive하는 트랜잭션이 커밋되는 상황") {
            val flagKey = "demo.feature.propagation-archive"
            transactionTemplate.execute {
                featureFlagDomainService.create(
                    CreateFeatureFlagCommand(
                        flagKey = flagKey,
                        type = FeatureFlagType.RELEASE,
                        strategy = EvaluationStrategy.GlobalToggle(enabled = true),
                        description = "archive propagation test",
                        actorUserId = 1L,
                    ),
                )
            }

            When("archive 트랜잭션을 커밋하면") {
                transactionTemplate.execute {
                    featureFlagDomainService.archive(ArchiveFeatureFlagCommand(key = flagKey, actorUserId = 1L))
                }

                Then("아카이브 이벤트도 전파가 트리거되어 캐시가 ARCHIVED로 갱신된다") {
                    // setup의 create 커밋에서 1회 + 본 When의 archive 커밋에서 1회 = 총 2회.
                    // archive 커밋이 실제로 전파를 트리거했다는 사실은 캐시가 ARCHIVED로 갱신된 것으로 확정한다.
                    verify(exactly = 2) { propagateFeatureFlagChangeUseCase.execute(flagKey) }
                    val snapshot = featureFlagCacheStore.get(flagKey)
                    snapshot.shouldNotBeNull()
                    snapshot.status shouldBe FeatureFlagStatus.ARCHIVED
                }
            }
        }
    }
}

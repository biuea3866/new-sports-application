package com.sportsapp.domain.featureflag.service

import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.featureflag.dto.ActivateFeatureFlagCommand
import com.sportsapp.domain.featureflag.dto.ArchiveFeatureFlagCommand
import com.sportsapp.domain.featureflag.dto.CreateFeatureFlagCommand
import com.sportsapp.domain.featureflag.dto.GetAuditLogsCommand
import com.sportsapp.domain.featureflag.dto.UpdateFeatureFlagCommand
import com.sportsapp.domain.featureflag.entity.FeatureFlag
import com.sportsapp.domain.featureflag.entity.FeatureFlagAuditLog
import com.sportsapp.domain.featureflag.entity.FeatureFlagChangeType
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.event.FeatureFlagChangedEvent
import com.sportsapp.domain.featureflag.exception.DuplicateFeatureFlagKeyException
import com.sportsapp.domain.featureflag.exception.FeatureFlagNotFoundException
import com.sportsapp.domain.featureflag.exception.FeatureFlagStatusConflictException
import com.sportsapp.domain.featureflag.gateway.FeatureFlagCacheStore
import com.sportsapp.domain.featureflag.gateway.FeatureFlagChangeBroadcaster
import com.sportsapp.domain.featureflag.repository.FeatureFlagAuditLogRepository
import com.sportsapp.domain.featureflag.repository.FeatureFlagRepository
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

private class FeatureFlagDomainServiceFixture {
    val featureFlagRepository = mockk<FeatureFlagRepository>()
    val featureFlagAuditLogRepository = mockk<FeatureFlagAuditLogRepository>()
    val featureFlagCacheStore = mockk<FeatureFlagCacheStore>(relaxed = true)
    val featureFlagChangeBroadcaster = mockk<FeatureFlagChangeBroadcaster>(relaxed = true)
    val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)

    val service = FeatureFlagDomainService(
        featureFlagRepository = featureFlagRepository,
        featureFlagAuditLogRepository = featureFlagAuditLogRepository,
        featureFlagCacheStore = featureFlagCacheStore,
        featureFlagChangeBroadcaster = featureFlagChangeBroadcaster,
        domainEventPublisher = domainEventPublisher,
    )

    init {
        every { featureFlagRepository.save(any()) } answers { firstArg() }
        every { featureFlagAuditLogRepository.save(any()) } answers { firstArg() }
    }
}

private fun newFlag(
    flagKey: String,
    type: FeatureFlagType = FeatureFlagType.RELEASE,
    strategy: EvaluationStrategy = EvaluationStrategy.GlobalToggle(enabled = true),
    description: String? = "demo flag",
): FeatureFlag = FeatureFlag.create(flagKey = flagKey, type = type, strategy = strategy, description = description)

/**
 * `FeatureFlagDomainService` CRUD·감사·전파 오케스트레이션 단위 테스트 (BE-04).
 *
 * 시나리오마다 독립된 Mock/서비스 인스턴스를 구성한다 — Kotest BehaviorSpec은 leaf 테스트에
 * 도달할 때마다 상위 Given/When 블록을 재실행하므로, mock을 최상단에서 공유하면 호출 카운트가
 * 누적돼 `verify(exactly = 0/1)` 단언이 다른 시나리오의 호출과 뒤섞인다.
 */
class FeatureFlagDomainServiceTest : BehaviorSpec({

    Given("존재하지 않는 key로 create를 호출하면") {
        val fixture = FeatureFlagDomainServiceFixture()
        every { fixture.featureFlagRepository.existsByKey("demo.feature.hello") } returns false

        When("생성하면") {
            val command = CreateFeatureFlagCommand(
                flagKey = "demo.feature.hello",
                type = FeatureFlagType.RELEASE,
                strategy = EvaluationStrategy.GlobalToggle(enabled = true),
                description = "demo flag",
                actorUserId = 1L,
            )
            val result = fixture.service.create(command)

            Then("save·감사(CREATED)·FeatureFlagChangedEvent 발행이 모두 일어난다") {
                result.flagKey shouldBe "demo.feature.hello"
                verify(exactly = 1) { fixture.featureFlagRepository.save(any()) }

                val auditSlot = slot<FeatureFlagAuditLog>()
                verify(exactly = 1) { fixture.featureFlagAuditLogRepository.save(capture(auditSlot)) }
                auditSlot.captured.changeType shouldBe FeatureFlagChangeType.CREATED
                auditSlot.captured.beforeSnapshot shouldBe null
                auditSlot.captured.afterSnapshot shouldBe result.toSnapshot()

                val eventsSlot = slot<List<DomainEvent>>()
                verify(exactly = 1) { fixture.domainEventPublisher.publishAll(capture(eventsSlot)) }
                eventsSlot.captured.size shouldBe 1
                (eventsSlot.captured.single() as FeatureFlagChangedEvent).flagKey shouldBe "demo.feature.hello"
            }
        }
    }

    Given("이미 존재하는 key로 create를 호출하면") {
        val fixture = FeatureFlagDomainServiceFixture()
        every { fixture.featureFlagRepository.existsByKey("demo.feature.duplicate") } returns true

        When("생성을 시도하면") {
            val command = CreateFeatureFlagCommand(
                flagKey = "demo.feature.duplicate",
                type = FeatureFlagType.RELEASE,
                strategy = EvaluationStrategy.GlobalToggle(enabled = true),
                description = null,
                actorUserId = 1L,
            )

            Then("DuplicateFeatureFlagKeyException을 던지고 save하지 않는다") {
                shouldThrow<DuplicateFeatureFlagKeyException> { fixture.service.create(command) }
                verify(exactly = 0) { fixture.featureFlagRepository.save(any()) }
                verify(exactly = 0) { fixture.featureFlagAuditLogRepository.save(any()) }
            }
        }
    }

    Given("존재하는 ACTIVE 플래그를 update하면") {
        val fixture = FeatureFlagDomainServiceFixture()
        val existing = newFlag(flagKey = "demo.feature.update")
        every { fixture.featureFlagRepository.findByKey("demo.feature.update") } returns existing

        When("전략과 설명을 변경하면") {
            val command = UpdateFeatureFlagCommand(
                key = "demo.feature.update",
                strategy = EvaluationStrategy.GlobalToggle(enabled = false),
                description = "updated",
                actorUserId = 2L,
            )
            val result = fixture.service.update(command)

            Then("감사 로그의 before는 수정 전, after는 수정 후 스냅샷이다") {
                val auditSlot = slot<FeatureFlagAuditLog>()
                verify(exactly = 1) { fixture.featureFlagAuditLogRepository.save(capture(auditSlot)) }
                auditSlot.captured.changeType shouldBe FeatureFlagChangeType.UPDATED
                auditSlot.captured.beforeSnapshot?.strategy shouldBe EvaluationStrategy.GlobalToggle(enabled = true)
                auditSlot.captured.afterSnapshot.strategy shouldBe EvaluationStrategy.GlobalToggle(enabled = false)
                result.description shouldBe "updated"
            }
        }
    }

    Given("존재하지 않는 key로 update를 호출하면") {
        val fixture = FeatureFlagDomainServiceFixture()
        every { fixture.featureFlagRepository.findByKey("demo.feature.missing") } returns null

        When("update를 호출하면") {
            Then("FeatureFlagNotFoundException을 던진다") {
                shouldThrow<FeatureFlagNotFoundException> {
                    fixture.service.update(
                        UpdateFeatureFlagCommand(
                            key = "demo.feature.missing",
                            strategy = EvaluationStrategy.GlobalToggle(enabled = true),
                            description = null,
                            actorUserId = 1L,
                        ),
                    )
                }
            }
        }
    }

    Given("존재하지 않는 key로 archive를 호출하면") {
        val fixture = FeatureFlagDomainServiceFixture()
        every { fixture.featureFlagRepository.findByKey("demo.feature.missing") } returns null

        When("archive를 호출하면") {
            Then("FeatureFlagNotFoundException을 던진다") {
                shouldThrow<FeatureFlagNotFoundException> {
                    fixture.service.archive(ArchiveFeatureFlagCommand(key = "demo.feature.missing", actorUserId = 1L))
                }
            }
        }
    }

    Given("이미 ARCHIVED인 플래그의 archive를 재호출하면") {
        val fixture = FeatureFlagDomainServiceFixture()
        val archived = newFlag(flagKey = "demo.feature.archived")
        archived.archive()
        archived.pullDomainEvents()
        every { fixture.featureFlagRepository.findByKey("demo.feature.archived") } returns archived

        When("archive를 호출하면") {
            Then("FeatureFlagStatusConflictException을 던진다") {
                shouldThrow<FeatureFlagStatusConflictException> {
                    fixture.service.archive(ArchiveFeatureFlagCommand(key = "demo.feature.archived", actorUserId = 1L))
                }
            }
        }
    }

    Given("ARCHIVED 플래그의 update를 호출하면") {
        val fixture = FeatureFlagDomainServiceFixture()
        val archived = newFlag(flagKey = "demo.feature.archived-update")
        archived.archive()
        archived.pullDomainEvents()
        every { fixture.featureFlagRepository.findByKey("demo.feature.archived-update") } returns archived

        When("update를 호출하면") {
            Then("FeatureFlagStatusConflictException을 던진다(수정 불가)") {
                shouldThrow<FeatureFlagStatusConflictException> {
                    fixture.service.update(
                        UpdateFeatureFlagCommand(
                            key = "demo.feature.archived-update",
                            strategy = EvaluationStrategy.GlobalToggle(enabled = false),
                            description = "nope",
                            actorUserId = 1L,
                        ),
                    )
                }
            }
        }
    }

    Given("ARCHIVED로 전이 가능한 ACTIVE 플래그를 archive 후 다시 activate하면") {
        val fixture = FeatureFlagDomainServiceFixture()
        val active = newFlag(flagKey = "demo.feature.activate-target")
        every { fixture.featureFlagRepository.findByKey("demo.feature.activate-target") } returns active

        When("archive 후 activate를 순서대로 호출하면") {
            fixture.service.archive(ArchiveFeatureFlagCommand(key = "demo.feature.activate-target", actorUserId = 1L))
            val result =
                fixture.service.activate(ActivateFeatureFlagCommand(key = "demo.feature.activate-target", actorUserId = 1L))

            Then("status가 ACTIVE로 전이되고 감사 로그가 각각 1건씩 적재된다") {
                result.status shouldBe FeatureFlagStatus.ACTIVE
                verify(exactly = 1) {
                    fixture.featureFlagAuditLogRepository.save(match { it.changeType == FeatureFlagChangeType.ARCHIVED })
                }
                verify(exactly = 1) {
                    fixture.featureFlagAuditLogRepository.save(match { it.changeType == FeatureFlagChangeType.ACTIVATED })
                }
            }
        }
    }

    Given("존재하는 key로 getByKey를 호출하면") {
        val fixture = FeatureFlagDomainServiceFixture()
        val flag = newFlag(flagKey = "demo.feature.get")
        every { fixture.featureFlagRepository.findByKey("demo.feature.get") } returns flag

        When("조회하면") {
            val result = fixture.service.getByKey("demo.feature.get")

            Then("해당 FeatureFlag를 반환한다") {
                result shouldBe flag
            }
        }
    }

    Given("status·type 필터로 findAll을 호출하면") {
        val fixture = FeatureFlagDomainServiceFixture()
        val flags = listOf(newFlag(flagKey = "demo.feature.list"))
        every { fixture.featureFlagRepository.findAll(FeatureFlagStatus.ACTIVE, FeatureFlagType.RELEASE) } returns flags

        When("조회하면") {
            val result = fixture.service.findAll(FeatureFlagStatus.ACTIVE, FeatureFlagType.RELEASE)

            Then("레포지토리 조회 결과를 그대로 반환한다") {
                result shouldBe flags
            }
        }
    }

    Given("감사 로그 페이징 조회를 요청하면") {
        val fixture = FeatureFlagDomainServiceFixture()
        val auditLog = FeatureFlagAuditLog.create(
            changeType = FeatureFlagChangeType.CREATED,
            actorUserId = 1L,
            before = null,
            after = newFlag(flagKey = "demo.feature.audit").toSnapshot(),
        )
        val pageable = PageRequest.of(0, 20)
        val page = PageImpl(listOf(auditLog), pageable, 1)
        every { fixture.featureFlagAuditLogRepository.findByFlagKey("demo.feature.audit", pageable) } returns page

        When("getAuditLogs를 호출하면") {
            val result = fixture.service.getAuditLogs(GetAuditLogsCommand(key = "demo.feature.audit", pageable = pageable))

            Then("레포지토리의 페이징 결과를 그대로 반환한다") {
                result shouldBe page
            }
        }
    }

    Given("존재하는 key의 전파(propagate) 요청") {
        val fixture = FeatureFlagDomainServiceFixture()
        val flag = newFlag(flagKey = "demo.feature.propagate")
        every { fixture.featureFlagRepository.findByKey("demo.feature.propagate") } returns flag

        When("propagate를 호출하면") {
            fixture.service.propagate("demo.feature.propagate")

            Then("cacheStore.put과 broadcaster.broadcast를 각각 1회 호출한다") {
                verify(exactly = 1) { fixture.featureFlagCacheStore.put(flag.toSnapshot()) }
                verify(exactly = 1) { fixture.featureFlagChangeBroadcaster.broadcast("demo.feature.propagate") }
            }
        }
    }
})

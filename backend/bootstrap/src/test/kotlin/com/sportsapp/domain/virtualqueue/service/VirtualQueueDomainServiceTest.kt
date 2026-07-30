package com.sportsapp.domain.virtualqueue.service

import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.virtualqueue.VirtualQueueFeatureFlagKeys
import com.sportsapp.domain.virtualqueue.exception.QueueEntryNotFoundException
import com.sportsapp.domain.virtualqueue.exception.QueueFullException
import com.sportsapp.domain.virtualqueue.gateway.EntryTokenIssuer
import com.sportsapp.domain.virtualqueue.gateway.VirtualQueueStore
import com.sportsapp.domain.virtualqueue.vo.EntryToken
import com.sportsapp.domain.virtualqueue.vo.QueueEntryState
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import com.sportsapp.domain.virtualqueue.vo.QueueTargetType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime
import org.springframework.dao.DataAccessResourceFailureException

private const val USER_ID = 100L
private const val MAX_CAPACITY = 100_000
private const val BATCH_SIZE = 100
private const val TICK_SECONDS = 2

class VirtualQueueDomainServiceTest : BehaviorSpec({

    val target = QueueTarget(QueueTargetType.LIMITED_DROP, 1L)

    fun token(raw: String = "token-raw"): EntryToken = EntryToken(raw = raw, expiresAt = ZonedDateTime.now().plusMinutes(5))

    fun buildService(
        virtualQueueStore: VirtualQueueStore = mockk(),
        entryTokenIssuer: EntryTokenIssuer = mockk(),
        featureFlagEvaluator: FeatureFlagEvaluator = mockk(),
    ) = VirtualQueueDomainService(
        virtualQueueStore = virtualQueueStore,
        entryTokenIssuer = entryTokenIssuer,
        featureFlagEvaluator = featureFlagEvaluator,
        maxCapacity = MAX_CAPACITY,
        batchSize = BATCH_SIZE,
        tickSeconds = TICK_SECONDS,
    )

    Given("플래그 ON·미포화 상태에서") {
        val virtualQueueStore = mockk<VirtualQueueStore>()
        val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()
        val service = buildService(virtualQueueStore = virtualQueueStore, featureFlagEvaluator = featureFlagEvaluator)

        every { featureFlagEvaluator.isEnabled(VirtualQueueFeatureFlagKeys.ENABLED, FeatureContext.of(USER_ID), false) } returns true
        every { virtualQueueStore.enterIfAbsent(target, USER_ID, MAX_CAPACITY) } returns 5L
        every { virtualQueueStore.registerActive(target) } returns Unit
        every { virtualQueueStore.rankOf(target, USER_ID) } returns 4
        every { virtualQueueStore.admittedCount(target) } returns 0L

        When("enter를 호출하면") {
            val result = service.enter(target, USER_ID)

            Then("seq를 부여한 WAITING을 반환한다") {
                result.state shouldBe QueueEntryState.WAITING
                result.position?.aheadCount shouldBe 4L
                result.position?.admitted shouldBe false
                result.entryToken shouldBe null
                verify(exactly = 1) { virtualQueueStore.enterIfAbsent(target, USER_ID, MAX_CAPACITY) }
                verify(exactly = 1) { virtualQueueStore.registerActive(target) }
            }
        }
    }

    Given("플래그 OFF 상태에서") {
        val virtualQueueStore = mockk<VirtualQueueStore>()
        val entryTokenIssuer = mockk<EntryTokenIssuer>()
        val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()
        val service = buildService(
            virtualQueueStore = virtualQueueStore,
            entryTokenIssuer = entryTokenIssuer,
            featureFlagEvaluator = featureFlagEvaluator,
        )
        val issuedToken = token()

        every { featureFlagEvaluator.isEnabled(VirtualQueueFeatureFlagKeys.ENABLED, FeatureContext.of(USER_ID), false) } returns false
        every { entryTokenIssuer.mintStateless(target, USER_ID) } returns issuedToken

        When("enter를 호출하면") {
            val result = service.enter(target, USER_ID)

            Then("Redis에 접근하지 않는 mintStateless로 발급해 DIRECT_ADMITTED(토큰 포함)를 반환한다 (플래그 OFF 경로는 인터셉터 검증 대상이 아니라 멱등 마커가 불필요)") {
                result.state shouldBe QueueEntryState.DIRECT_ADMITTED
                result.entryToken shouldBe issuedToken
                result.position shouldBe null
                verify(exactly = 0) { virtualQueueStore.enterIfAbsent(any(), any(), any()) }
                verify(exactly = 1) { entryTokenIssuer.mintStateless(target, USER_ID) }
                verify(exactly = 0) { entryTokenIssuer.issueIfAbsent(any(), any()) }
            }
        }
    }

    Given("플래그 OFF 상태에서 Redis가 다운된 상황에서") {
        val virtualQueueStore = mockk<VirtualQueueStore>()
        val entryTokenIssuer = mockk<EntryTokenIssuer>()
        val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()
        val service = buildService(
            virtualQueueStore = virtualQueueStore,
            entryTokenIssuer = entryTokenIssuer,
            featureFlagEvaluator = featureFlagEvaluator,
        )
        val statelessToken = token("stateless-token-off")

        every { featureFlagEvaluator.isEnabled(VirtualQueueFeatureFlagKeys.ENABLED, FeatureContext.of(USER_ID), false) } returns false
        // issueIfAbsent는 Redis SET NX를 타므로 Redis 다운 시 예외를 던진다 — OFF 경로가 이 메서드를
        // 호출하면 5xx가 된다. mintStateless는 Redis에 접근하지 않아 다운 상황에서도 안전하다.
        every { entryTokenIssuer.issueIfAbsent(target, USER_ID) } throws DataAccessResourceFailureException("redis down")
        every { entryTokenIssuer.mintStateless(target, USER_ID) } returns statelessToken

        When("enter를 호출하면") {
            val result = service.enter(target, USER_ID)

            Then("예외 전파 없이 mintStateless로 DIRECT_ADMITTED를 반환한다 (5xx 방지)") {
                result.state shouldBe QueueEntryState.DIRECT_ADMITTED
                result.entryToken shouldBe statelessToken
                verify(exactly = 1) { entryTokenIssuer.mintStateless(target, USER_ID) }
                verify(exactly = 0) { entryTokenIssuer.issueIfAbsent(any(), any()) }
            }
        }
    }

    Given("동일 userId가 재진입하는 상황에서") {
        val virtualQueueStore = mockk<VirtualQueueStore>()
        val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()
        val service = buildService(virtualQueueStore = virtualQueueStore, featureFlagEvaluator = featureFlagEvaluator)

        every { featureFlagEvaluator.isEnabled(VirtualQueueFeatureFlagKeys.ENABLED, FeatureContext.of(USER_ID), false) } returns true
        every { virtualQueueStore.enterIfAbsent(target, USER_ID, MAX_CAPACITY) } returns 7L
        every { virtualQueueStore.registerActive(target) } returns Unit
        every { virtualQueueStore.rankOf(target, USER_ID) } returns 2
        every { virtualQueueStore.admittedCount(target) } returns 0L

        When("enter를 두 번 호출하면") {
            val first = service.enter(target, USER_ID)
            val second = service.enter(target, USER_ID)

            Then("새 순번 없이 기존 seq 기반 WAITING을 동일하게 반환한다 (멱등, FR-2)") {
                first.position?.aheadCount shouldBe second.position?.aheadCount
                first.state shouldBe QueueEntryState.WAITING
                second.state shouldBe QueueEntryState.WAITING
                verify(exactly = 2) { virtualQueueStore.enterIfAbsent(target, USER_ID, MAX_CAPACITY) }
            }
        }
    }

    Given("대기열이 포화(store가 null 반환)된 상황에서") {
        val virtualQueueStore = mockk<VirtualQueueStore>()
        val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()
        val service = buildService(virtualQueueStore = virtualQueueStore, featureFlagEvaluator = featureFlagEvaluator)

        every { featureFlagEvaluator.isEnabled(VirtualQueueFeatureFlagKeys.ENABLED, FeatureContext.of(USER_ID), false) } returns true
        every { virtualQueueStore.enterIfAbsent(target, USER_ID, MAX_CAPACITY) } returns null

        When("enter를 호출하면") {
            Then("QueueFull 예외를 던진다 (FR-7)") {
                shouldThrow<QueueFullException> { service.enter(target, USER_ID) }
            }
        }
    }

    Given("status 조회 시 seq <= admittedCount(admission 판정 통과)인 상황에서") {
        val virtualQueueStore = mockk<VirtualQueueStore>()
        val entryTokenIssuer = mockk<EntryTokenIssuer>()
        val service = buildService(virtualQueueStore = virtualQueueStore, entryTokenIssuer = entryTokenIssuer)
        val issuedToken = token()

        every { virtualQueueStore.touchHeartbeat(target, USER_ID) } returns Unit
        every { virtualQueueStore.seqOf(target, USER_ID) } returns 3L
        every { virtualQueueStore.rankOf(target, USER_ID) } returns 0
        every { virtualQueueStore.admittedCount(target) } returns 5L
        every { entryTokenIssuer.issueIfAbsent(target, USER_ID) } returns issuedToken
        every { virtualQueueStore.leave(target, USER_ID) } returns Unit

        When("status를 호출하면") {
            val result = service.status(target, USER_ID)

            Then("토큰을 발급하고 큐에서 제거해 ADMITTED를 반환한다") {
                result.state shouldBe QueueEntryState.ADMITTED
                result.entryToken shouldBe issuedToken
                verify(exactly = 1) { virtualQueueStore.touchHeartbeat(target, USER_ID) }
                verify(exactly = 1) { entryTokenIssuer.issueIfAbsent(target, USER_ID) }
                verify(exactly = 1) { virtualQueueStore.leave(target, USER_ID) }
            }
        }
    }

    Given("앞선 사용자들이 leave로 빠져 rank가 0으로 붕괴했지만 seq > admittedCount인 상황에서") {
        val virtualQueueStore = mockk<VirtualQueueStore>()
        val entryTokenIssuer = mockk<EntryTokenIssuer>()
        val service = buildService(virtualQueueStore = virtualQueueStore, entryTokenIssuer = entryTokenIssuer)

        every { virtualQueueStore.touchHeartbeat(target, USER_ID) } returns Unit
        every { virtualQueueStore.seqOf(target, USER_ID) } returns 10L
        every { virtualQueueStore.rankOf(target, USER_ID) } returns 0
        every { virtualQueueStore.admittedCount(target) } returns 5L

        When("status를 호출하면") {
            val result = service.status(target, USER_ID)

            Then("고정 seq 기준으로 판정해 WAITING을 유지한다 (§0-1 연쇄 admission 회귀)") {
                result.state shouldBe QueueEntryState.WAITING
                result.position?.aheadCount shouldBe 0L
                result.position?.admitted shouldBe false
                verify(exactly = 0) { entryTokenIssuer.issueIfAbsent(any(), any()) }
                verify(exactly = 0) { virtualQueueStore.leave(any(), any()) }
            }
        }
    }

    Given("status 조회 시 seq > admittedCount(아직 대기)인 상황에서") {
        val virtualQueueStore = mockk<VirtualQueueStore>()
        val entryTokenIssuer = mockk<EntryTokenIssuer>()
        val service = buildService(virtualQueueStore = virtualQueueStore, entryTokenIssuer = entryTokenIssuer)

        every { virtualQueueStore.touchHeartbeat(target, USER_ID) } returns Unit
        every { virtualQueueStore.seqOf(target, USER_ID) } returns 8L
        every { virtualQueueStore.rankOf(target, USER_ID) } returns 3
        every { virtualQueueStore.admittedCount(target) } returns 5L

        When("status를 호출하면") {
            val result = service.status(target, USER_ID)

            Then("heartbeat만 갱신하고 aheadCount·ETA를 담은 WAITING을 반환한다") {
                result.state shouldBe QueueEntryState.WAITING
                result.position?.aheadCount shouldBe 3L
                result.position?.etaSeconds shouldBe (result.position?.etaSeconds ?: -1L)
                verify(exactly = 1) { virtualQueueStore.touchHeartbeat(target, USER_ID) }
                verify(exactly = 0) { entryTokenIssuer.issueIfAbsent(any(), any()) }
            }
        }
    }

    Given("status 조회 시 큐에 존재하지 않는(seqOf가 null) 상황에서") {
        val virtualQueueStore = mockk<VirtualQueueStore>()
        val service = buildService(virtualQueueStore = virtualQueueStore)

        every { virtualQueueStore.touchHeartbeat(target, USER_ID) } returns Unit
        every { virtualQueueStore.seqOf(target, USER_ID) } returns null

        When("status를 호출하면") {
            Then("QueueEntryNotFound 예외를 던진다") {
                shouldThrow<QueueEntryNotFoundException> { service.status(target, USER_ID) }
            }
        }
    }

    Given("enter 도중 Redis 장애(DataAccessException)가 발생한 상황에서") {
        val virtualQueueStore = mockk<VirtualQueueStore>()
        val entryTokenIssuer = mockk<EntryTokenIssuer>()
        val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()
        val service = buildService(
            virtualQueueStore = virtualQueueStore,
            entryTokenIssuer = entryTokenIssuer,
            featureFlagEvaluator = featureFlagEvaluator,
        )
        val statelessToken = token("stateless-token")

        every { featureFlagEvaluator.isEnabled(VirtualQueueFeatureFlagKeys.ENABLED, FeatureContext.of(USER_ID), false) } returns true
        every { virtualQueueStore.enterIfAbsent(target, USER_ID, MAX_CAPACITY) } throws DataAccessResourceFailureException("redis down")
        every { entryTokenIssuer.mintStateless(target, USER_ID) } returns statelessToken

        When("enter를 호출하면") {
            val result = service.enter(target, USER_ID)

            // redis_degraded 카운터는 infra(VirtualQueueStoreImpl.executeTracked)에서만 증가시킨다 —
            // 도메인 서비스가 같은 예외를 다시 세면 단일 장애가 두 번 집계된다 (p3).
            Then("mintStateless로 토큰을 발급해 directEntry로 폴백한다 (fail-open, §0-3)") {
                result.state shouldBe QueueEntryState.DIRECT_ADMITTED
                result.entryToken shouldBe statelessToken
                verify(exactly = 1) { entryTokenIssuer.mintStateless(target, USER_ID) }
                verify(exactly = 0) { entryTokenIssuer.issueIfAbsent(any(), any()) }
            }
        }
    }

    Given("status 조회 도중 Redis 장애(DataAccessException)가 발생한 상황에서") {
        val virtualQueueStore = mockk<VirtualQueueStore>()
        val entryTokenIssuer = mockk<EntryTokenIssuer>()
        val service = buildService(
            virtualQueueStore = virtualQueueStore,
            entryTokenIssuer = entryTokenIssuer,
        )
        val statelessToken = token("stateless-token-2")

        every { virtualQueueStore.touchHeartbeat(target, USER_ID) } throws DataAccessResourceFailureException("redis down")
        every { entryTokenIssuer.mintStateless(target, USER_ID) } returns statelessToken

        When("status를 호출하면") {
            val result = service.status(target, USER_ID)

            Then("mintStateless로 토큰을 발급해 directEntry로 폴백한다 (fail-open, §0-3)") {
                result.state shouldBe QueueEntryState.DIRECT_ADMITTED
                result.entryToken shouldBe statelessToken
                verify(exactly = 1) { entryTokenIssuer.mintStateless(target, USER_ID) }
            }
        }
    }

    Given("이탈을 요청하는 상황에서") {
        val virtualQueueStore = mockk<VirtualQueueStore>()
        val service = buildService(virtualQueueStore = virtualQueueStore)

        every { virtualQueueStore.leave(target, USER_ID) } returns Unit

        When("leave를 호출하면") {
            service.leave(target, USER_ID)

            Then("store.leave에 위임한다") {
                verify(exactly = 1) { virtualQueueStore.leave(target, USER_ID) }
            }
        }
    }

    Given("운영자 통계를 조회하는 상황에서 (운영자 통계 조회)") {
        val virtualQueueStore = mockk<VirtualQueueStore>()
        val service = buildService(virtualQueueStore = virtualQueueStore)

        every { virtualQueueStore.waitingSize(target) } returns 250L
        every { virtualQueueStore.admittedCount(target) } returns 100L

        When("stats를 호출하면") {
            val result = service.stats(target)

            Then("waitingCount·admittedCount는 Store 즉시 조회값을 그대로 담는다") {
                result.waitingCount shouldBe 250L
                result.admittedCount shouldBe 100L
                verify(exactly = 1) { virtualQueueStore.waitingSize(target) }
                verify(exactly = 1) { virtualQueueStore.admittedCount(target) }
            }

            Then("지표성 필드(admissionRate·avgWait·p95Wait)는 BE-10 미연동 상태라 0.0 placeholder다") {
                result.admissionRatePerSec shouldBe 0.0
                result.avgWaitSeconds shouldBe 0.0
                result.p95WaitSeconds shouldBe 0.0
            }
        }
    }

    Given("대기 인원이 0건인 큐의 통계를 조회하는 상황에서") {
        val virtualQueueStore = mockk<VirtualQueueStore>()
        val service = buildService(virtualQueueStore = virtualQueueStore)

        every { virtualQueueStore.waitingSize(target) } returns 0L
        every { virtualQueueStore.admittedCount(target) } returns 0L

        When("stats를 호출하면") {
            val result = service.stats(target)

            Then("예외 없이 0건 통계를 반환한다") {
                result.waitingCount shouldBe 0L
                result.admittedCount shouldBe 0L
            }
        }
    }
})

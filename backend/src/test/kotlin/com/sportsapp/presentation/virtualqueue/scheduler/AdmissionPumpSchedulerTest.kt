package com.sportsapp.presentation.virtualqueue.scheduler

import com.sportsapp.SharedTestContainers
import com.sportsapp.application.virtualqueue.usecase.RunAdmissionBatchUseCase
import com.sportsapp.domain.virtualqueue.dto.AdmissionBatchResult
import com.sportsapp.domain.virtualqueue.gateway.VirtualQueueStore
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import com.sportsapp.domain.virtualqueue.vo.QueueTargetType
import com.sportsapp.infrastructure.lock.RedisDistributedLock
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.support.TestPropertySourceUtils

/**
 * `AdmissionPumpScheduler` — 실 Redis(Testcontainers) 분산 락 기반 클러스터 단일 전진 검증(BE-07).
 *
 * `RedisDistributedLockConcurrencyTest`의 경량 SpringBootTest(Redis 전용) 패턴을 따른다.
 * `VirtualQueueStore`·`RunAdmissionBatchUseCase`는 Mock — 검증 대상은 "락 획득 여부에 따른 위임 분기"와
 * "동시 pump에도 정확히 1회만 실행"이다.
 */
@SpringBootTest(classes = [AdmissionPumpSchedulerTest.TestApp::class])
@ContextConfiguration(initializers = [AdmissionPumpSchedulerTest.RedisInitializer::class])
@TestPropertySource(
    properties = [
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration," +
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
    ],
)
class AdmissionPumpSchedulerTest @Autowired constructor(
    private val redisTemplate: StringRedisTemplate,
) : BehaviorSpec({

    fun buildScheduler(
        activeTargets: Set<QueueTarget>,
        runAdmissionBatchUseCase: RunAdmissionBatchUseCase,
        admissionEnabled: Boolean = true,
    ): AdmissionPumpScheduler {
        val store = mockk<VirtualQueueStore>()
        every { store.activeTargets() } returns activeTargets
        return AdmissionPumpScheduler(
            virtualQueueStore = store,
            distributedLock = RedisDistributedLock(redisTemplate),
            runAdmissionBatchUseCase = runAdmissionBatchUseCase,
            admissionEnabled = admissionEnabled,
            batchSize = 100,
            staleSeconds = 60,
            maxEvictPerTick = 500,
        )
    }

    Given("활성 대상 1건에 대해 분산 락을 획득할 수 있는 상태에서") {
        val target = QueueTarget(QueueTargetType.LIMITED_DROP, 9001L)
        redisTemplate.delete(target.admissionLockKey())
        val useCase = mockk<RunAdmissionBatchUseCase>()
        every { useCase.execute(any()) } returns AdmissionBatchResult(admittedCount = 100L, evictedCount = 0)
        val scheduler = buildScheduler(setOf(target), useCase)

        When("pump를 호출하면") {
            scheduler.pump()

            Then("RunAdmissionBatchUseCase를 대상에 대해 1회 호출한다") {
                verify(exactly = 1) { useCase.execute(match { it.target == target }) }
            }
        }
    }

    Given("대상의 분산 락을 다른 인스턴스가 이미 보유한 상태에서") {
        val target = QueueTarget(QueueTargetType.LIMITED_DROP, 9002L)
        redisTemplate.delete(target.admissionLockKey())
        redisTemplate.opsForValue().set(target.admissionLockKey(), "other-instance", Duration.ofMillis(1900))
        val useCase = mockk<RunAdmissionBatchUseCase>()
        val scheduler = buildScheduler(setOf(target), useCase)

        When("pump를 호출하면") {
            scheduler.pump()

            Then("RunAdmissionBatchUseCase를 호출하지 않고 스킵한다 (클러스터 단일 전진)") {
                verify(exactly = 0) { useCase.execute(any()) }
            }
        }
    }

    Given("virtual-queue.admission.enabled=false (운영 킬 스위치)인 상태에서") {
        val target = QueueTarget(QueueTargetType.LIMITED_DROP, 9003L)
        redisTemplate.delete(target.admissionLockKey())
        val useCase = mockk<RunAdmissionBatchUseCase>()
        val scheduler = buildScheduler(setOf(target), useCase, admissionEnabled = false)

        When("pump를 호출하면") {
            scheduler.pump()

            Then("배치 실행 자체를 건너뛴다") {
                verify(exactly = 0) { useCase.execute(any()) }
            }
        }
    }

    Given("배치 실행 중 UseCase가 예외를 던지는 상태에서") {
        val target = QueueTarget(QueueTargetType.LIMITED_DROP, 9004L)
        redisTemplate.delete(target.admissionLockKey())
        val useCase = mockk<RunAdmissionBatchUseCase>()
        every { useCase.execute(any()) } throws IllegalStateException("boom")
        val scheduler = buildScheduler(setOf(target), useCase)

        When("pump를 호출하면") {
            Then("예외를 삼키고 스케줄러 스레드가 죽지 않는다 (다음 틱 재시도)") {
                shouldNotThrowAny { scheduler.pump() }
            }
        }
    }

    Given("빈 활성 대상 셋인 상태에서") {
        val useCase = mockk<RunAdmissionBatchUseCase>()
        val scheduler = buildScheduler(emptySet(), useCase)

        When("pump를 호출하면") {
            scheduler.pump()

            Then("아무 대상도 처리하지 않는다 (no-op)") {
                verify(exactly = 0) { useCase.execute(any()) }
            }
        }
    }

    Given("두 인스턴스(서로 다른 AdmissionPumpScheduler)가 같은 대상에 동시에 pump하는 상황 (클러스터 단일 전진)") {
        val target = QueueTarget(QueueTargetType.LIMITED_DROP, 9005L)
        redisTemplate.delete(target.admissionLockKey())
        val sharedUseCase = mockk<RunAdmissionBatchUseCase>()
        every { sharedUseCase.execute(any()) } returns AdmissionBatchResult(admittedCount = 100L, evictedCount = 0)
        val schedulerA = buildScheduler(setOf(target), sharedUseCase)
        val schedulerB = buildScheduler(setOf(target), sharedUseCase)

        When("두 인스턴스가 동시에 pump를 호출하면") {
            val executor = Executors.newFixedThreadPool(2)
            val ready = CountDownLatch(2)
            val start = CountDownLatch(1)
            val tasks = listOf(schedulerA, schedulerB).map { scheduler ->
                executor.submit {
                    ready.countDown()
                    start.await()
                    scheduler.pump()
                }
            }
            ready.await()
            start.countDown()
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
            executor.shutdown()

            Then("RunAdmissionBatchUseCase는 대상당 정확히 1회만 실행된다 (admitted_count 전진은 한 인스턴스분만)") {
                verify(exactly = 1) { sharedUseCase.execute(match { it.target == target }) }
            }
        }
    }
}) {
    // 명시적으로 컴포넌트 스캔을 하지 않는다 — 같은 패키지의 실제 `AdmissionPumpScheduler`
    // `@Component`가 스캔되면 이 경량 테스트 컨텍스트에 없는 실 빈(VirtualQueueStore·DistributedLock·
    // RunAdmissionBatchUseCase)을 요구해 기동이 실패한다. 검증 대상은 수동 생성한 인스턴스다.
    @SpringBootConfiguration
    @EnableAutoConfiguration
    class TestApp

    class RedisInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(applicationContext: ConfigurableApplicationContext) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                applicationContext,
                "spring.data.redis.host=${SharedTestContainers.redis.host}",
                "spring.data.redis.port=${SharedTestContainers.redis.getMappedPort(6379)}",
            )
        }
    }

    companion object {
        init {
            SharedTestContainers.redis
        }
    }
}

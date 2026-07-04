package com.sportsapp.presentation.goods.worker

import com.sportsapp.application.goods.usecase.ReconcileLimitedDropsUseCase
import com.sportsapp.domain.common.exceptions.RedisLockException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.scheduling.annotation.Scheduled
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions

class DropReconciliationWorkerTest : BehaviorSpec({

    Given("스케줄 주기가 도래한 상황") {
        val reconcileLimitedDropsUseCase = mockk<ReconcileLimitedDropsUseCase>()
        val worker = DropReconciliationWorker(reconcileLimitedDropsUseCase)

        every { reconcileLimitedDropsUseCase.execute() } returns Unit

        When("reconcile을 호출하면") {
            worker.reconcile()

            Then("ReconcileLimitedDropsUseCase를 위임 호출한다") {
                verify(exactly = 1) { reconcileLimitedDropsUseCase.execute() }
            }
        }
    }

    Given("대사 도중 Redis 접근 장애(DataAccessException)가 발생하는 상황") {
        val reconcileLimitedDropsUseCase = mockk<ReconcileLimitedDropsUseCase>()
        val worker = DropReconciliationWorker(reconcileLimitedDropsUseCase)

        every { reconcileLimitedDropsUseCase.execute() } throws DataAccessResourceFailureException("redis down")

        When("reconcile을 호출하면") {
            Then("예외를 전파하지 않고 스케줄러 스레드를 보호한다") {
                shouldNotThrowAny { worker.reconcile() }
            }
        }
    }

    Given("대사 도중 Redis 분산 락 장애(RedisLockException)가 발생하는 상황") {
        val reconcileLimitedDropsUseCase = mockk<ReconcileLimitedDropsUseCase>()
        val worker = DropReconciliationWorker(reconcileLimitedDropsUseCase)

        every { reconcileLimitedDropsUseCase.execute() } throws RedisLockException("lock failure")

        When("reconcile을 호출하면") {
            Then("예외를 전파하지 않고 스케줄러 스레드를 보호한다") {
                shouldNotThrowAny { worker.reconcile() }
            }
        }
    }

    Given("DropReconciliationWorker의 스케줄 설정") {
        When("reconcile 메서드의 @Scheduled 어노테이션을 조회하면") {
            val scheduled = DropReconciliationWorker::class.memberFunctions
                .first { it.name == "reconcile" }
                .findAnnotation<Scheduled>()

            Then("초기 지연(initialDelayString)이 설정돼 부팅 직후 즉시 실행되지 않는다") {
                scheduled.shouldNotBeNull()
                scheduled.initialDelayString.isNotBlank() shouldBe true
            }
        }
    }
})

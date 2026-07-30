package com.sportsapp.infrastructure.goods.redis

import com.sportsapp.domain.goods.gateway.DropReservationStore
import com.sportsapp.infrastructure.goods.retry.LimitedDropRetryProperties
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.retry.context.RetryContextSupport
import org.springframework.retry.support.RetrySynchronizationManager
import org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED
import org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK
import org.springframework.transaction.support.TransactionSynchronizationManager

private const val DROP_ID = 1L
private const val USER_ID = 100L
private const val QUANTITY = 1
private const val IDEMPOTENCY_KEY = "idem-key-1"

/**
 * [DropReservationCompensatorImpl] 단위 테스트 — FIX-02.
 *
 * 실 DB 트랜잭션 없이 `TransactionSynchronizationManager`(Spring 트랜잭션 동기화)와
 * `RetrySynchronizationManager`(Spring Retry 컨텍스트)를 직접 조작해, 커밋 단계 실패까지 포괄하는
 * 보상 등록이 "재시도 예산이 소진되는 마지막 시도"에서만 실제 취소를 실행하는지 검증한다.
 * 실 트랜잭션·재시도를 통한 종단 검증은 `scenario/goods`의 시나리오 테스트가 담당한다.
 */
class DropReservationCompensatorImplTest : BehaviorSpec({

    fun buildProperties(maxAttempts: Int) = LimitedDropRetryProperties().apply { this.maxAttempts = maxAttempts }

    fun completeTransaction(status: Int) {
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCompletion(status) }
    }

    afterEach {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
        RetrySynchronizationManager.clear()
    }

    Given("Admitted로 등록하고, 재시도 예산이 남아 있는 중간 시도가 롤백되는 상황") {
        val dropReservationStore = mockk<DropReservationStore>()
        val compensator = DropReservationCompensatorImpl(dropReservationStore, buildProperties(maxAttempts = 3))
        TransactionSynchronizationManager.initSynchronization()
        // retryCount=0 → 1번째 시도, maxAttempts=3이므로 마지막 시도가 아니다.
        RetrySynchronizationManager.register(RetryContextSupport(null))

        When("registerCancelOnRollback(admittedThisAttempt=true) 등록 후 트랜잭션이 롤백되면") {
            compensator.registerCancelOnRollback(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY, admittedThisAttempt = true)
            completeTransaction(STATUS_ROLLED_BACK)

            Then("[FIX-02] 마지막 시도가 아니므로 예약을 취소하지 않는다") {
                verify(exactly = 0) { dropReservationStore.cancel(any(), any(), any(), any()) }
            }
        }
    }

    Given("Admitted로 등록하고, 재시도 예산이 소진되는 마지막 시도가 롤백되는 상황") {
        val dropReservationStore = mockk<DropReservationStore>()
        every { dropReservationStore.cancel(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY) } returns Unit
        val compensator = DropReservationCompensatorImpl(dropReservationStore, buildProperties(maxAttempts = 3))
        TransactionSynchronizationManager.initSynchronization()
        val retryContext = RetryContextSupport(null)
        retryContext.registerThrowable(RuntimeException("attempt 1 failed"))
        retryContext.registerThrowable(RuntimeException("attempt 2 failed"))
        // retryCount=2 → 3번째(마지막) 시도, maxAttempts=3.
        RetrySynchronizationManager.register(retryContext)

        When("registerCancelOnRollback(admittedThisAttempt=true) 등록 후 트랜잭션이 롤백되면") {
            compensator.registerCancelOnRollback(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY, admittedThisAttempt = true)
            completeTransaction(STATUS_ROLLED_BACK)

            Then("[FIX-02] 예약 취소가 정확히 1회 수행된다 (커밋 단계 실패까지 포괄 — 핵심 RED)") {
                verify(exactly = 1) { dropReservationStore.cancel(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY) }
            }
        }
    }

    Given("같은 재시도 시퀀스 안에서 Admitted 이후 AlreadyReserved로 이어지는 마지막 시도가 롤백되는 상황") {
        val dropReservationStore = mockk<DropReservationStore>()
        every { dropReservationStore.cancel(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY) } returns Unit
        val compensator = DropReservationCompensatorImpl(dropReservationStore, buildProperties(maxAttempts = 3))
        val retryContext = RetryContextSupport(null)
        RetrySynchronizationManager.register(retryContext)

        // 1번째 시도: Admitted (owned 표시) → 롤백되지만 마지막이 아니므로 no-op.
        TransactionSynchronizationManager.initSynchronization()
        compensator.registerCancelOnRollback(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY, admittedThisAttempt = true)
        completeTransaction(STATUS_ROLLED_BACK)
        TransactionSynchronizationManager.clearSynchronization()
        retryContext.registerThrowable(RuntimeException("attempt 1 failed"))

        // 2번째 시도 시작 시점 retryCount=1 → 이번이 2번째 시도. 이 시도에서도 실패한다고 가정하고
        // 3번째(마지막) 시도로 넘어가기 전 상태를 만든다.
        retryContext.registerThrowable(RuntimeException("attempt 2 failed"))
        // retryCount=2 → 3번째(마지막) 시도.
        TransactionSynchronizationManager.initSynchronization()

        When("registerCancelOnRollback(admittedThisAttempt=false)를 3번째 시도에서 등록 후 롤백되면") {
            compensator.registerCancelOnRollback(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY, admittedThisAttempt = false)
            completeTransaction(STATUS_ROLLED_BACK)

            Then("[FIX-02] 같은 시퀀스의 이전 Admitted를 근거로 마지막 시도에서 예약이 취소된다") {
                verify(exactly = 1) { dropReservationStore.cancel(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY) }
            }
        }
    }

    Given("Admitted 표시 없이(다른 외부 호출의 마커로) AlreadyReserved만 등록하는 상황") {
        val dropReservationStore = mockk<DropReservationStore>()
        val compensator = DropReservationCompensatorImpl(dropReservationStore, buildProperties(maxAttempts = 1))
        TransactionSynchronizationManager.initSynchronization()
        RetrySynchronizationManager.register(RetryContextSupport(null))

        When("registerCancelOnRollback(admittedThisAttempt=false)만 등록 후 롤백되면") {
            compensator.registerCancelOnRollback(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY, admittedThisAttempt = false)
            completeTransaction(STATUS_ROLLED_BACK)

            Then("[FIX-02] 내 예약임을 표시한 적이 없으므로 취소를 호출하지 않는다 (다른 호출의 마커 오취소 방지)") {
                verify(exactly = 0) { dropReservationStore.cancel(any(), any(), any(), any()) }
            }
        }
    }

    Given("트랜잭션 동기화가 활성화되지 않은 단독 호출 상황") {
        val dropReservationStore = mockk<DropReservationStore>()
        val compensator = DropReservationCompensatorImpl(dropReservationStore, buildProperties(maxAttempts = 20))

        When("registerCancelOnRollback을 호출하면") {
            Then("[FIX-02] 예외 없이 등록을 건너뛴다") {
                compensator.registerCancelOnRollback(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY, admittedThisAttempt = true)
                verify(exactly = 0) { dropReservationStore.cancel(any(), any(), any(), any()) }
            }
        }
    }

    Given("트랜잭션이 정상 커밋으로 완료되는 상황") {
        val dropReservationStore = mockk<DropReservationStore>()
        val compensator = DropReservationCompensatorImpl(dropReservationStore, buildProperties(maxAttempts = 1))
        TransactionSynchronizationManager.initSynchronization()
        RetrySynchronizationManager.register(RetryContextSupport(null))

        When("registerCancelOnRollback 등록 후 커밋(STATUS_COMMITTED)되면") {
            compensator.registerCancelOnRollback(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY, admittedThisAttempt = true)
            completeTransaction(STATUS_COMMITTED)

            Then("[FIX-02] 취소를 호출하지 않는다") {
                verify(exactly = 0) { dropReservationStore.cancel(any(), any(), any(), any()) }
            }
        }
    }
})

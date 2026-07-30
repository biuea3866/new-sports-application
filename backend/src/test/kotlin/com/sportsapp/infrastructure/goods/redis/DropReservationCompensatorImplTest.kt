package com.sportsapp.infrastructure.goods.redis

import com.sportsapp.domain.goods.gateway.DropReservationStore
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
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
 * [DropReservationCompensatorImpl] 단위 테스트 — FIX-02, code-review p1 재설계.
 *
 * 실 DB 트랜잭션·재시도 없이 `TransactionSynchronizationManager`(Spring 트랜잭션 동기화)와
 * `RetrySynchronizationManager`(Spring Retry 컨텍스트)를 직접 조작해, 롤백 시 실제 취소를 즉시
 * 실행하지 않고 [RetryContext][org.springframework.retry.RetryContext]에 취소 후보만 남기는지
 * 검증한다. "재시도 시퀀스 종료 시점에만 실제 취소가 실행된다"는
 * [DropReservationCompensationRetryListenerTest]가 담당하고, 실 트랜잭션·재시도를 통한 종단
 * 검증은 `scenario/goods`의 시나리오 테스트가 담당한다.
 */
class DropReservationCompensatorImplTest : BehaviorSpec({

    fun completeTransaction(status: Int) {
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCompletion(status) }
    }

    afterEach {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
        RetrySynchronizationManager.clear()
    }

    Given("재시도 컨텍스트가 있는 상태에서 Admitted로 등록하고 롤백되는 상황") {
        val dropReservationStore = mockk<DropReservationStore>()
        val compensator = DropReservationCompensatorImpl(dropReservationStore)
        TransactionSynchronizationManager.initSynchronization()
        val retryContext = RetryContextSupport(null)
        RetrySynchronizationManager.register(retryContext)

        When("registerCancelOnRollback(admittedThisAttempt=true) 등록 후 트랜잭션이 롤백되면") {
            compensator.registerCancelOnRollback(
                dropId = DROP_ID,
                userId = USER_ID,
                quantity = QUANTITY,
                idempotencyKey = IDEMPOTENCY_KEY,
                admittedThisAttempt = true,
            )
            completeTransaction(STATUS_ROLLED_BACK)

            Then("[code-review p1] 즉시 취소하지 않고 재시도 컨텍스트에 취소 후보만 남긴다") {
                verify(exactly = 0) { dropReservationStore.cancel(any(), any(), any(), any()) }
                val pendingCancellation =
                    retryContext.getAttribute(DropReservationCompensatorImpl.PENDING_CANCELLATION_ATTRIBUTE)
                pendingCancellation shouldBe PendingReservationCancellation(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY)
            }
        }
    }

    Given("같은 재시도 시퀀스 안에서 Admitted 이후 AlreadyReserved로 이어지는 시도가 롤백되는 상황") {
        val dropReservationStore = mockk<DropReservationStore>()
        val compensator = DropReservationCompensatorImpl(dropReservationStore)
        val retryContext = RetryContextSupport(null)
        RetrySynchronizationManager.register(retryContext)

        // 1번째 시도: Admitted (owned 표시) → 롤백되어 취소 후보가 남는다.
        TransactionSynchronizationManager.initSynchronization()
        compensator.registerCancelOnRollback(
            dropId = DROP_ID,
            userId = USER_ID,
            quantity = QUANTITY,
            idempotencyKey = IDEMPOTENCY_KEY,
            admittedThisAttempt = true,
        )
        completeTransaction(STATUS_ROLLED_BACK)
        TransactionSynchronizationManager.clearSynchronization()

        // 2번째 시도 시작.
        TransactionSynchronizationManager.initSynchronization()

        When("registerCancelOnRollback(admittedThisAttempt=false)를 등록 후 롤백되면") {
            compensator.registerCancelOnRollback(
                dropId = DROP_ID,
                userId = USER_ID,
                quantity = QUANTITY,
                idempotencyKey = IDEMPOTENCY_KEY,
                admittedThisAttempt = false,
            )
            completeTransaction(STATUS_ROLLED_BACK)

            Then("[FIX-02] 같은 시퀀스의 이전 Admitted를 근거로 취소 후보가 갱신된다 (실제 취소는 아직 없음)") {
                verify(exactly = 0) { dropReservationStore.cancel(any(), any(), any(), any()) }
                val pendingCancellation =
                    retryContext.getAttribute(DropReservationCompensatorImpl.PENDING_CANCELLATION_ATTRIBUTE)
                pendingCancellation shouldBe PendingReservationCancellation(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY)
            }
        }
    }

    Given("Admitted 표시 없이(다른 외부 호출의 마커로) AlreadyReserved만 등록하는 상황") {
        val dropReservationStore = mockk<DropReservationStore>()
        val compensator = DropReservationCompensatorImpl(dropReservationStore)
        TransactionSynchronizationManager.initSynchronization()
        val retryContext = RetryContextSupport(null)
        RetrySynchronizationManager.register(retryContext)

        When("registerCancelOnRollback(admittedThisAttempt=false)만 등록 후 롤백되면") {
            compensator.registerCancelOnRollback(
                dropId = DROP_ID,
                userId = USER_ID,
                quantity = QUANTITY,
                idempotencyKey = IDEMPOTENCY_KEY,
                admittedThisAttempt = false,
            )
            completeTransaction(STATUS_ROLLED_BACK)

            Then("[FIX-02] 내 예약임을 표시한 적이 없으므로 취소 후보를 남기지 않는다 (다른 호출의 마커 오취소 방지)") {
                verify(exactly = 0) { dropReservationStore.cancel(any(), any(), any(), any()) }
                retryContext.getAttribute(DropReservationCompensatorImpl.PENDING_CANCELLATION_ATTRIBUTE).shouldBeNull()
            }
        }
    }

    Given("재시도 컨텍스트가 없는(재시도로 감싸이지 않은) 단독 호출이 롤백되는 상황") {
        val dropReservationStore = mockk<DropReservationStore>()
        every { dropReservationStore.cancel(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY) } returns Unit
        val compensator = DropReservationCompensatorImpl(dropReservationStore)
        TransactionSynchronizationManager.initSynchronization()

        When("registerCancelOnRollback(admittedThisAttempt=true) 등록 후 트랜잭션이 롤백되면") {
            compensator.registerCancelOnRollback(
                dropId = DROP_ID,
                userId = USER_ID,
                quantity = QUANTITY,
                idempotencyKey = IDEMPOTENCY_KEY,
                admittedThisAttempt = true,
            )
            completeTransaction(STATUS_ROLLED_BACK)

            Then("[code-review p1] 재시도가 아예 없으므로 이 시도 자체가 마지막 — 즉시 취소한다") {
                verify(exactly = 1) { dropReservationStore.cancel(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY) }
            }
        }
    }

    Given("트랜잭션 동기화가 활성화되지 않은 단독 호출 상황") {
        val dropReservationStore = mockk<DropReservationStore>()
        val compensator = DropReservationCompensatorImpl(dropReservationStore)

        When("registerCancelOnRollback을 호출하면") {
            Then("[FIX-02] 예외 없이 등록을 건너뛴다") {
                compensator.registerCancelOnRollback(
                    dropId = DROP_ID,
                    userId = USER_ID,
                    quantity = QUANTITY,
                    idempotencyKey = IDEMPOTENCY_KEY,
                    admittedThisAttempt = true,
                )
                verify(exactly = 0) { dropReservationStore.cancel(any(), any(), any(), any()) }
            }
        }
    }

    Given("트랜잭션이 정상 커밋으로 완료되는 상황") {
        val dropReservationStore = mockk<DropReservationStore>()
        val compensator = DropReservationCompensatorImpl(dropReservationStore)
        TransactionSynchronizationManager.initSynchronization()
        val retryContext = RetryContextSupport(null)
        RetrySynchronizationManager.register(retryContext)

        When("registerCancelOnRollback 등록 후 커밋(STATUS_COMMITTED)되면") {
            compensator.registerCancelOnRollback(
                dropId = DROP_ID,
                userId = USER_ID,
                quantity = QUANTITY,
                idempotencyKey = IDEMPOTENCY_KEY,
                admittedThisAttempt = true,
            )
            completeTransaction(STATUS_COMMITTED)

            Then("[FIX-02] 취소 후보를 남기지 않고 취소도 호출하지 않는다") {
                verify(exactly = 0) { dropReservationStore.cancel(any(), any(), any(), any()) }
                retryContext.getAttribute(DropReservationCompensatorImpl.PENDING_CANCELLATION_ATTRIBUTE).shouldBeNull()
            }
        }
    }
})

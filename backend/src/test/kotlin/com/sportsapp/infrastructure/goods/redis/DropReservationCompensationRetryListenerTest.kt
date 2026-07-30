package com.sportsapp.infrastructure.goods.redis

import com.sportsapp.domain.goods.gateway.DropReservationStore
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.retry.RetryCallback
import org.springframework.retry.context.RetryContextSupport

private const val DROP_ID = 1L
private const val USER_ID = 100L
private const val QUANTITY = 1
private const val IDEMPOTENCY_KEY = "idem-key-1"

/**
 * [DropReservationCompensationRetryListener] 단위 테스트 — FIX-02, code-review p1.
 *
 * 실 `RetryTemplate` 없이 [DropReservationCompensationRetryListener.close]를 직접 호출해,
 * "재시도 시퀀스가 최종적으로 실패했을 때만(throwable != null) 남아 있는 취소 후보를 실제로
 * 취소한다"는 계약을 검증한다. `RetryTemplate.doExecute`는 시퀀스당 이 메서드를 정확히 1회
 * 호출하며, `throwable`은 성공 시 null·최종 실패 시 마지막 예외다 (RetryTemplate.java 확인).
 */
class DropReservationCompensationRetryListenerTest : BehaviorSpec({

    val callback = mockk<RetryCallback<Any, Throwable>>()

    Given("재시도 시퀀스가 최종 실패로 끝나고, 취소 후보가 남아 있는 상황") {
        val dropReservationStore = mockk<DropReservationStore>()
        every { dropReservationStore.cancel(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY) } returns Unit
        val listener = DropReservationCompensationRetryListener(dropReservationStore)
        val context = RetryContextSupport(null)
        context.setAttribute(
            DropReservationCompensatorImpl.PENDING_CANCELLATION_ATTRIBUTE,
            PendingReservationCancellation(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY),
        )

        When("close(throwable=마지막 예외)를 호출하면") {
            listener.close(context, callback, IllegalStateException("commit failed"))

            Then("[code-review p1] 남아 있는 취소 후보를 실제로 취소한다") {
                verify(exactly = 1) { dropReservationStore.cancel(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY) }
            }
        }
    }

    Given("재시도 시퀀스가 성공으로 끝나고, 이전 시도의 취소 후보가 남아 있는 상황") {
        val dropReservationStore = mockk<DropReservationStore>()
        val listener = DropReservationCompensationRetryListener(dropReservationStore)
        val context = RetryContextSupport(null)
        context.setAttribute(
            DropReservationCompensatorImpl.PENDING_CANCELLATION_ATTRIBUTE,
            PendingReservationCancellation(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY),
        )

        When("close(throwable=null)를 호출하면") {
            listener.close(context, callback, null)

            Then("[FIX-02] 성공했으므로 남아 있는 후보를 무시하고 취소하지 않는다") {
                verify(exactly = 0) { dropReservationStore.cancel(any(), any(), any(), any()) }
            }
        }
    }

    Given("취소 후보가 전혀 등록되지 않은(이 시퀀스와 무관한) 재시도가 최종 실패로 끝나는 상황") {
        val dropReservationStore = mockk<DropReservationStore>()
        val listener = DropReservationCompensationRetryListener(dropReservationStore)
        val context = RetryContextSupport(null)

        When("close(throwable=마지막 예외)를 호출하면") {
            listener.close(context, callback, IllegalStateException("unrelated failure"))

            Then("[FIX-02] no-op — 이 리스너는 전역이지만 다른 @Retryable 시퀀스에 영향을 주지 않는다") {
                verify(exactly = 0) { dropReservationStore.cancel(any(), any(), any(), any()) }
            }
        }
    }

    Given("보상 취소 자체가 실패하는(Redis 장애) 상황") {
        val dropReservationStore = mockk<DropReservationStore>()
        every {
            dropReservationStore.cancel(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY)
        } throws IllegalStateException("redis down")
        val listener = DropReservationCompensationRetryListener(dropReservationStore)
        val context = RetryContextSupport(null)
        context.setAttribute(
            DropReservationCompensatorImpl.PENDING_CANCELLATION_ATTRIBUTE,
            PendingReservationCancellation(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY),
        )

        When("close(throwable=마지막 예외)를 호출하면") {
            Then("[code-review p3] 예외를 흡수하고 흔적을 로그로 남긴다 (재시도 인프라로 전파 방지)") {
                shouldNotThrowAny { listener.close(context, callback, IllegalStateException("commit failed")) }
                verify(exactly = 1) { dropReservationStore.cancel(DROP_ID, USER_ID, QUANTITY, IDEMPOTENCY_KEY) }
            }
        }
    }
})

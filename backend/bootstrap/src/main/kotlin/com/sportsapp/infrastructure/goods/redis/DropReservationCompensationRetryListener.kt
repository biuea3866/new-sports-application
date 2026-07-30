package com.sportsapp.infrastructure.goods.redis

import com.sportsapp.domain.goods.gateway.DropReservationStore
import org.slf4j.LoggerFactory
import org.springframework.retry.RetryCallback
import org.springframework.retry.RetryContext
import org.springframework.retry.RetryListener
import org.springframework.stereotype.Component

/**
 * 재시도 시퀀스가 완전히 끝난 시점에 남아 있는 예약 취소 후보를 실제로 취소한다
 * (FIX-02, code-review p1).
 *
 * `RetryTemplate.doExecute`는 전체 재시도 시퀀스(모든 시도)에 걸쳐 [RetryContext] 하나를
 * 공유하고, [close]는 그 시퀀스가 끝날 때 정확히 1회만 호출된다 — 성공하면 `throwable`이
 * `null`, 재시도 예산이 소진되거나 재시도 대상이 아닌 예외로 즉시 끝나면 마지막 예외가 담긴다.
 * "몇 번째 시도인지" 산술로는 재시도 종료 여부를 판정할 수 없다(재시도 여부는 발생한 예외의
 * 종류에 따라 갈리고, 그 종류는 등록 시점엔 알 수 없다) — 이 리스너가 시퀀스 종료 시점에
 * 판정을 넘겨받는 이유다.
 *
 * `@Retryable`이 걸린 다른 모든 메서드에도 전역으로 적용되는 리스너지만,
 * [DropReservationCompensatorImpl]이 등록한 [DropReservationCompensatorImpl.PENDING_CANCELLATION_ATTRIBUTE]
 * 속성이 없는 시퀀스에는 아무 영향을 주지 않는다(no-op).
 */
@Component
class DropReservationCompensationRetryListener(
    private val dropReservationStore: DropReservationStore,
) : RetryListener {

    private val log = LoggerFactory.getLogger(DropReservationCompensationRetryListener::class.java)

    override fun <T, E : Throwable> close(
        context: RetryContext,
        callback: RetryCallback<T, E>,
        throwable: Throwable?,
    ) {
        if (throwable == null) return
        val pendingCancellation = context.getAttribute(DropReservationCompensatorImpl.PENDING_CANCELLATION_ATTRIBUTE)
            as? PendingReservationCancellation ?: return

        runCatching {
            dropReservationStore.cancel(
                pendingCancellation.dropId,
                pendingCancellation.userId,
                pendingCancellation.quantity,
                pendingCancellation.idempotencyKey,
            )
        }.onSuccess {
            log.info(
                "DropReservationCompensationRetryListener: dropId={} userId={} idempotencyKey={} 재시도 종료 후 보상 취소를 발화했다",
                pendingCancellation.dropId,
                pendingCancellation.userId,
                pendingCancellation.idempotencyKey,
            )
        }.onFailure { exception ->
            log.error(
                "DropReservationCompensationRetryListener: dropId={} userId={} idempotencyKey={} 재시도 종료 후 보상 취소 실패",
                pendingCancellation.dropId,
                pendingCancellation.userId,
                pendingCancellation.idempotencyKey,
                exception,
            )
        }
    }
}

package com.sportsapp.infrastructure.goods.redis

import com.sportsapp.domain.goods.gateway.DropReservationCompensator
import com.sportsapp.domain.goods.gateway.DropReservationStore
import com.sportsapp.infrastructure.goods.retry.LimitedDropRetryProperties
import org.springframework.retry.RetryContext
import org.springframework.retry.support.RetrySynchronizationManager
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * [DropReservationCompensator] Spring 트랜잭션 동기화 훅 구현 (FIX-02).
 *
 * Admitted 직후(또는 같은 재시도 시퀀스 안의 후속 AlreadyReserved 시도) 등록되어, 진행 중인
 * 트랜잭션이 롤백으로 완료되면 [DropReservationStore.cancel]을 호출한다. "재시도 예산이 소진되는
 * 마지막 시도"인지는 [RetrySynchronizationManager]의 현재 시도 횟수와
 * [LimitedDropRetryProperties.maxAttempts]를 대조해 판단한다 — 중간 시도의 롤백에서는 아무것도
 * 하지 않아, 예약을 유지한 채(재시도 시 AlreadyReserved로 이어짐) 다음 시도로 넘어간다.
 *
 * `admittedThisAttempt`가 false(AlreadyReserved)인 호출은, 같은 재시도 시퀀스([RetryContext]가
 * 시도 사이에 공유되는 성질을 이용) 안에서 이미 Admitted가 표시된 경우에만 등록을 이어간다 —
 * 서로 다른 외부 호출이 우연히 같은 idempotencyKey로 AlreadyReserved를 받는 경우(멱등 재요청 등)까지
 * 등록하면 남의 예약을 잘못 취소할 위험이 있다.
 */
@Component
class DropReservationCompensatorImpl(
    private val dropReservationStore: DropReservationStore,
    private val limitedDropRetryProperties: LimitedDropRetryProperties,
) : DropReservationCompensator {

    override fun registerCancelOnRollback(
        dropId: Long,
        userId: Long,
        quantity: Int,
        idempotencyKey: String,
        admittedThisAttempt: Boolean,
    ) {
        val retryContext = RetrySynchronizationManager.getContext()
        if (!isOwnedByCurrentRetry(retryContext, admittedThisAttempt)) return
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return

        val isFinalAttempt = isFinalAttempt(retryContext)
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCompletion(status: Int) {
                if (status != TransactionSynchronization.STATUS_ROLLED_BACK) return
                if (!isFinalAttempt) return
                dropReservationStore.cancel(dropId, userId, quantity, idempotencyKey)
            }
        })
    }

    /**
     * Admitted면 이번 재시도 시퀀스가 "내 예약"임을 [RetryContext]에 표시하고 항상 true를 반환한다.
     * AlreadyReserved면, 같은 시퀀스 안에서 이미 그 표시가 되어 있을 때만 true를 반환한다.
     */
    private fun isOwnedByCurrentRetry(retryContext: RetryContext?, admittedThisAttempt: Boolean): Boolean {
        if (admittedThisAttempt) {
            retryContext?.setAttribute(OWNED_BY_CURRENT_RETRY_ATTRIBUTE, true)
            return true
        }
        return retryContext?.getAttribute(OWNED_BY_CURRENT_RETRY_ATTRIBUTE) == true
    }

    /**
     * 현재 시도가 재시도 예산의 마지막 시도인지 판단한다. `@Retryable` 컨텍스트가 없으면(재시도로
     * 감싸이지 않은 단독 호출) 더 이상의 재시도가 없으므로 마지막 시도로 간주한다.
     *
     * [RetryContext.getRetryCount]는 "지금까지의 실패 횟수"라 이번이 몇 번째 시도인지는
     * `retryCount + 1`이다 — Spring Retry는 `retryCount < maxAttempts`일 때만 다음 시도를
     * 진행하므로(`SimpleRetryPolicy.canRetry`), `retryCount + 1 >= maxAttempts`면 이번이
     * 마지막으로 허용된 시도다.
     */
    private fun isFinalAttempt(retryContext: RetryContext?): Boolean {
        if (retryContext == null) return true
        return retryContext.retryCount + 1 >= limitedDropRetryProperties.maxAttempts
    }

    companion object {
        private const val OWNED_BY_CURRENT_RETRY_ATTRIBUTE = "dropReservation.ownedByCurrentRetry"
    }
}

package com.sportsapp.infrastructure.goods.redis

import com.sportsapp.domain.goods.gateway.DropReservationCompensator
import com.sportsapp.domain.goods.gateway.DropReservationStore
import org.slf4j.LoggerFactory
import org.springframework.retry.RetryContext
import org.springframework.retry.support.RetrySynchronizationManager
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * [DropReservationCompensator] Spring 트랜잭션 동기화 훅 구현 (FIX-02, code-review p1 재설계).
 *
 * Admitted 직후(또는 같은 재시도 시퀀스 안의 후속 AlreadyReserved 시도) 등록되어, 진행 중인
 * 트랜잭션이 롤백으로 완료되면 예약 취소 후보를 [RetryContext]에 남긴다. **실제
 * [DropReservationStore.cancel] 호출은 이 클래스가 하지 않는다** — [DropReservationCompensationRetryListener]가
 * 재시도 시퀀스 전체가 끝난 뒤(throwable != null, 즉 최종 실패) 그 후보를 읽어 실행한다.
 *
 * 등록 시점(트랜잭션 커밋 전)에는 "이번이 재시도 예산이 소진되는 마지막 시도인지"를 알 수 없다 —
 * `retryCount`와 `maxAttempts`를 비교하는 산술은 이번 시도가 어떤 예외로 실패할지(재시도 대상인지
 * 아닌지)를 미리 안다고 가정하는데, `@Retryable(retryFor = [...])`에 없는 예외는 시도 횟수와
 * 무관하게 즉시 재시도가 끝난다(code-review p1 — 이전 산술 기반 구현은 이 경로에서 보상이
 * 유실됐다). 그래서 실제 취소 여부는 재시도 프레임워크가 "더 이상 재시도가 없다"고 확정하는
 * 시점(시퀀스 종료)까지 미룬다 — [RetryContext]는 한 시퀀스의 모든 시도에 걸쳐 공유되므로, 여러
 * 시도가 반복해 후보를 덮어써도 시퀀스 종료 시점엔 마지막으로 기록된 후보만 남는다.
 *
 * `@Retryable` 컨텍스트가 아예 없는 단독 호출(재시도로 감싸이지 않음)은 이 호출 자체가 더 이상의
 * 재시도가 없는 마지막 시도이므로, 롤백 시 즉시 취소한다.
 */
@Component
class DropReservationCompensatorImpl(
    private val dropReservationStore: DropReservationStore,
) : DropReservationCompensator {

    private val log = LoggerFactory.getLogger(DropReservationCompensatorImpl::class.java)

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

        val pendingCancellation = PendingReservationCancellation(dropId, userId, quantity, idempotencyKey)
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCompletion(status: Int) {
                if (status != TransactionSynchronization.STATUS_ROLLED_BACK) return
                if (retryContext == null) {
                    cancelImmediately(pendingCancellation)
                    return
                }
                retryContext.setAttribute(PENDING_CANCELLATION_ATTRIBUTE, pendingCancellation)
            }
        })
    }

    /**
     * 재시도로 감싸이지 않은 단독 호출의 롤백 — 이 호출 자체가 마지막 시도이므로 즉시 취소한다.
     * `TransactionSynchronizationUtils.invokeAfterCompletion`은 `afterCompletion`에서 던진 예외를
     * 삼키므로(F1이 조용한 누수였던 이유), 실패 시 로그로 흔적을 남긴다.
     */
    private fun cancelImmediately(pendingCancellation: PendingReservationCancellation) {
        runCatching {
            dropReservationStore.cancel(
                pendingCancellation.dropId,
                pendingCancellation.userId,
                pendingCancellation.quantity,
                pendingCancellation.idempotencyKey,
            )
        }.onFailure { exception ->
            log.error(
                "DropReservationCompensatorImpl: dropId={} userId={} idempotencyKey={} 단독 호출 롤백 보상 취소 실패",
                pendingCancellation.dropId,
                pendingCancellation.userId,
                pendingCancellation.idempotencyKey,
                exception,
            )
        }
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

    companion object {
        /** [DropReservationCompensationRetryListener]가 시퀀스 종료 시점에 읽는 속성 키. */
        const val PENDING_CANCELLATION_ATTRIBUTE = "dropReservation.pendingCancellation"
        private const val OWNED_BY_CURRENT_RETRY_ATTRIBUTE = "dropReservation.ownedByCurrentRetry"
    }
}

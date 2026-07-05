package com.sportsapp.presentation.featureflag.worker

import com.sportsapp.application.featureflag.usecase.PropagateFeatureFlagChangeUseCase
import com.sportsapp.domain.featureflag.event.FeatureFlagChangedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 관리 쓰기 트랜잭션 커밋 후 캐시 갱신 + pub/sub 브로드캐스트를 트리거하는 연결부 (BE-07).
 *
 * `@TransactionalEventListener(AFTER_COMMIT)`이라 미커밋 상태의 변경은 전파되지 않는다.
 */
@Component
class FeatureFlagChangedEventWorker(
    private val propagateFeatureFlagChangeUseCase: PropagateFeatureFlagChangeUseCase,
) {
    private val log = LoggerFactory.getLogger(FeatureFlagChangedEventWorker::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onFeatureFlagChanged(event: FeatureFlagChangedEvent) {
        try {
            propagateFeatureFlagChangeUseCase.execute(event.flagKey)
        } catch (exception: Exception) {
            log.error(
                "피처 플래그 변경 전파 실패 — flagKey={}",
                event.flagKey,
                exception,
            )
        }
    }
}

package com.sportsapp.presentation.virtualqueue.scheduler

import com.sportsapp.application.virtualqueue.dto.RunAdmissionBatchCommand
import com.sportsapp.application.virtualqueue.usecase.IsAdmissionPumpEnabledUseCase
import com.sportsapp.application.virtualqueue.usecase.ListActiveQueueTargetsUseCase
import com.sportsapp.application.virtualqueue.usecase.RunAdmissionBatchUseCase
import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import java.time.Duration
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 가상 대기열 Admission Pump — 활성 대상([ListActiveQueueTargetsUseCase])을 틱(2초, BE-01
 * `virtual-queue.admission.tick-seconds`)마다 순회해 배치 admission 전진·이탈 방출을 트리거한다
 * (BE-07, TDD "서버 토폴로지"·redis-contract §3). presentation layer는 domain interface
 * (`VirtualQueueStore`)를 직접 참조하지 않고, 대상 열거·킬 스위치 판정 모두 application UseCase
 * (`ListActiveQueueTargetsUseCase`·`IsAdmissionPumpEnabledUseCase`)를 경유한다(TDD "시스템 역할
 * 경계" — 이 스케줄러의 의존은 UseCase·`DistributedLock`(domain.common)뿐).
 *
 * **클러스터 단일 전진**: 3 replica 전부 이 스케줄러를 갖되, 대상별 Redis 분산 락
 * (`queue:admission:{type}:{id}`, `SET NX PX 1900ms`)을 획득한 단 1개 인스턴스만 실제
 * `RunAdmissionBatchUseCase`를 호출한다. 락 미획득은 정상 스킵(다른 인스턴스가 전진 중) — 대기·재시도
 * 없음. 락 TTL(1,900ms) < 틱 주기(2,000ms)라 다음 틱 전 자연 만료되며, 명시적 unlock은 하지 않는다
 * (redis-contract §3 — unlock 시도 중 예외로 놓치는 경우를 없애고, 어차피 자연 만료로 다음 틱에
 * 자동 승계된다).
 *
 * `GuestExpiryScheduler` 선례를 따라 대상 1건의 처리 실패가 스케줄러 스레드를 죽이지 않도록
 * try-catch로 보호한다 — 다음 틱에 그 대상만 재시도된다.
 *
 * 롤백: 운영 킬 스위치([IsAdmissionPumpEnabledUseCase] → `FeatureFlagEvaluator` 런타임 조회,
 * `virtual-queue.admission.enabled`)를 OFF로 활성화하면 배치 실행 자체를 재기동 없이 즉시
 * 중단할 수 있다(no-conditional-on-property — 부팅 고정 `@Value` 토글 아님).
 */
@Component
class AdmissionPumpScheduler(
    private val listActiveQueueTargetsUseCase: ListActiveQueueTargetsUseCase,
    private val isAdmissionPumpEnabledUseCase: IsAdmissionPumpEnabledUseCase,
    private val distributedLock: DistributedLock,
    private val runAdmissionBatchUseCase: RunAdmissionBatchUseCase,
    @Value("\${virtual-queue.admission.batch-size:100}") private val batchSize: Int,
    @Value("\${virtual-queue.heartbeat.stale-seconds:60}") private val staleSeconds: Long,
    @Value("\${virtual-queue.heartbeat.max-evict-per-tick:500}") private val maxEvictPerTick: Int,
) {
    private val log = LoggerFactory.getLogger(AdmissionPumpScheduler::class.java)

    /** 부팅 시 1회 생성되는 인스턴스 식별자 — 분산 락 소유자 값으로 사용한다. */
    private val instanceId: String = UUID.randomUUID().toString()

    @Scheduled(fixedDelayString = "#{\${virtual-queue.admission.tick-seconds:2} * 1000}")
    fun pump() {
        if (!isAdmissionPumpEnabledUseCase.execute()) {
            log.info("AdmissionPumpScheduler: disabled by virtual-queue.admission.enabled feature flag, skipping")
            return
        }
        listActiveQueueTargetsUseCase.execute().forEach(::pumpTarget)
    }

    private fun pumpTarget(target: QueueTarget) {
        try {
            if (!distributedLock.tryLock(target.admissionLockKey(), instanceId, LOCK_LEASE)) {
                return
            }
            val result = runAdmissionBatchUseCase.execute(
                RunAdmissionBatchCommand(
                    target = target,
                    batchSize = batchSize,
                    staleSeconds = staleSeconds,
                    maxEvictPerTick = maxEvictPerTick,
                ),
            )
            log.debug("AdmissionPumpScheduler: pumped target={} result={}", target, result)
        } catch (exception: Exception) {
            log.error("AdmissionPumpScheduler: batch failed for target=$target", exception)
        }
    }

    companion object {
        private val LOCK_LEASE: Duration = Duration.ofMillis(1900)
    }
}

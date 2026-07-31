package com.sportsapp.infrastructure.virtualqueue.metrics

import com.sportsapp.domain.virtualqueue.gateway.VirtualQueueStore
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.annotation.PostConstruct
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicReference
import org.springframework.stereotype.Component

/**
 * 가상 대기열 Observability 게이지·타이머 바인더(BE-10, TDD "Observability").
 *
 * `VirtualQueueStore`(domain interface, BE-02)만 읽어 아래 3종 지표를 옵저버빌리티 스택
 * (`/actuator/prometheus` → `observability/prometheus/prometheus.yml` `app` 잡)에 노출한다.
 * 소스 태그는 전부 `virtual-queue`(`SOURCE_TAG`=`SOURCE_VALUE`) — 지능형 장애 알림 규칙이
 * `AlertSource`를 이 태그로 구분한다(알림 규칙 등록 자체는 옵저버빌리티/알림 과제 소관, 이 티켓은
 * 지표 노출까지만).
 *
 * - `virtual_queue.length`(게이지) — 활성 대상(`store.activeTargets()`) 전체의
 *   `store.waitingSize` 합. 활성 대상이 0건이면 0을 노출한다(빈 상태 엣지).
 * - `virtual_queue.admission_rate`(게이지) — 활성 대상 전체의 `admittedCount` 합을 매 조회
 *   시점마다 직전 조회값과 비교해 초당 전진 속도로 환산한다(누적 카운터의 순간 델타/경과초).
 *   최초 조회는 기준점만 세워 0.0을 반환하고, 음수 델타(대상 리셋 등)는 0으로 바닥 처리한다.
 * - `virtual_queue.wait_seconds`(Timer, P50/P95/P99) — [recordWaitSeconds]로 기록한다. 대기
 *   시작~admission까지의 실제 경과를 넘겨줄 호출부(`VirtualQueueDomainService.admitEntry` 계측)는
 *   이 티켓 범위 밖(Single Writer: metrics 신규 파일만) — 이 바인더는 계측 지점(Timer)만
 *   선점 등록해 후속 티켓이 [recordWaitSeconds]를 호출하도록 준비한다.
 *
 * **카운터는 이 클래스가 소유하지 않는다** — `token` 발급/소진/만료·`bypass_attempt`는
 * BE-03(`HmacEntryTokenGateway`)·BE-09(`EntryTokenGateInterceptor`)가 자기 파일에서 직접
 * `meterRegistry.counter(...)`로 증가시킨다(현재 origin/main 시점 기준 BE-08/BE-09는 아직
 * 미착수라 두 카운터는 아직 코드베이스에 없다 — 이 바인더가 새로 만들지 않고, 착수 시 각 티켓이
 * 소유한다). `virtual_queue.redis_degraded`도 이미 `VirtualQueueStoreImpl.executeTracked`가
 * 등록해 두므로(IW2) 여기서 재등록하지 않는다 — Micrometer는 동일 이름을 다른 타입으로 재등록하면
 * 예외를 던진다(`FeatureFlagMetricsBinder`가 `feature_flag_propagation_lag_seconds`에 적용한 동일
 * 회피 패턴).
 *
 * **stats API 연동 갭(인계)**: `GetQueueStatsUseCase`가 반환하는 `QueueStats`의
 * `admissionRatePerSec`/`avgWaitSeconds`/`p95WaitSeconds` 3필드는 현재 0.0 placeholder다
 * (`QueueStats.of` 문서 참고). 이 바인더는 **push 전용**(Prometheus가 스크레이프하는
 * `/actuator/prometheus`) 지표만 노출하고 request-time 조회 API를 갖지 않아, 그 자체로는 stats
 * API를 채우지 못한다. 다만 게이지·타이머 이름을 stats 필드와 1:1 매칭 가능하게 설계했다
 * (`virtual_queue.admission_rate` ↔ `admissionRatePerSec`, `virtual_queue.wait_seconds`
 * mean/P95 ↔ `avgWaitSeconds`/`p95WaitSeconds`) — 후속 티켓이 `MeterRegistry.find(...)`로 이
 * 바인더가 등록한 게이지/타이머를 직접 조회해 `GetQueueStatsUseCase`를 채우거나, `MetricsQueryGateway`
 * 같은 domain interface를 신설해 이 바인더의 등록값을 읽어가면 된다(이번 티켓 스코프 밖 — 조회
 * Gateway 신설은 하지 않는다).
 */
@Component
class VirtualQueueMetricsBinder(
    private val meterRegistry: MeterRegistry,
    private val virtualQueueStore: VirtualQueueStore,
) {

    private val lastAdmissionSample = AtomicReference<AdmissionSample?>(null)
    private lateinit var waitSecondsTimer: Timer

    @PostConstruct
    fun bindMeters() {
        Gauge.builder(QUEUE_LENGTH_GAUGE, this) { it.totalWaitingSize() }
            .tag(SOURCE_TAG, SOURCE_VALUE)
            .description("가상 대기열 현재 대기 인원 — 활성 대상 waitingSize 합")
            .register(meterRegistry)

        Gauge.builder(ADMISSION_RATE_GAUGE, this) { it.admissionRatePerSecond() }
            .tag(SOURCE_TAG, SOURCE_VALUE)
            .description("가상 대기열 admission 전진 속도(초당 명) — 활성 대상 admittedCount 합의 순간 델타")
            .register(meterRegistry)

        waitSecondsTimer = Timer.builder(WAIT_SECONDS_TIMER)
            .tag(SOURCE_TAG, SOURCE_VALUE)
            .description("가상 대기열 진입~admission 대기시간 분포(P50/P95/P99)")
            .publishPercentiles(PERCENTILE_P50, PERCENTILE_P95, PERCENTILE_P99)
            .register(meterRegistry)
    }

    /**
     * 대기시간(초)을 [WAIT_SECONDS_TIMER]에 기록한다. 실제 대기 시작~admission 경과를 넘겨주는
     * 호출부 연동은 이 티켓 범위 밖이다(클래스 문서 "wait_seconds" 항목 참고) — 후속 티켓이 이
     * 메서드를 호출하도록 계측 지점만 선점한다.
     */
    fun recordWaitSeconds(waitSeconds: Double) {
        waitSecondsTimer.record(Duration.ofNanos((waitSeconds * NANOS_PER_SECOND).toLong()))
    }

    private fun totalWaitingSize(): Double =
        virtualQueueStore.activeTargets().sumOf { virtualQueueStore.waitingSize(it) }.toDouble()

    private fun admissionRatePerSecond(): Double {
        val currentTotal = virtualQueueStore.activeTargets().sumOf { virtualQueueStore.admittedCount(it) }
        val now = ZonedDateTime.now()
        val previous = lastAdmissionSample.getAndSet(AdmissionSample(total = currentTotal, sampledAt = now))
            ?: return BASELINE_RATE

        val elapsedSeconds = Duration.between(previous.sampledAt, now).toMillis() / MILLIS_PER_SECOND
        if (elapsedSeconds <= 0.0) return BASELINE_RATE

        val delta = currentTotal - previous.total
        return (delta / elapsedSeconds).coerceAtLeast(BASELINE_RATE)
    }

    private data class AdmissionSample(val total: Long, val sampledAt: ZonedDateTime)

    companion object {
        const val QUEUE_LENGTH_GAUGE = "virtual_queue.length"
        const val ADMISSION_RATE_GAUGE = "virtual_queue.admission_rate"
        const val WAIT_SECONDS_TIMER = "virtual_queue.wait_seconds"
        const val SOURCE_TAG = "source"
        const val SOURCE_VALUE = "virtual-queue"

        private const val BASELINE_RATE = 0.0
        private const val MILLIS_PER_SECOND = 1000.0
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private const val PERCENTILE_P50 = 0.5
        private const val PERCENTILE_P95 = 0.95
        private const val PERCENTILE_P99 = 0.99
    }
}

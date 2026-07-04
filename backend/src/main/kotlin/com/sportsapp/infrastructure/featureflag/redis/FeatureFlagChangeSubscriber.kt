package com.sportsapp.infrastructure.featureflag.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.domain.featureflag.repository.FeatureFlagRepository
import com.sportsapp.infrastructure.featureflag.local.LocalFeatureFlagStore
import com.sportsapp.infrastructure.featureflag.metrics.FeatureFlagCacheMetrics
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import java.time.Duration
import java.time.ZonedDateTime
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.stereotype.Component

/**
 * `featureflag:changes` 채널 구독자 — 캐시 코히런스 메커니즘 전용(도메인/UseCase 미경유).
 *
 * Repository는 여기서 직접 호출하지 않는다(no-repository-in-consumer 예외는 이 리스너가 아니라
 * `LocalFeatureFlagStore.refresh` 내부 폴백에만 한정 — 티켓 명시).
 */
@Component
@ConditionalOnBean(FeatureFlagRepository::class)
class FeatureFlagChangeSubscriber(
    private val localFeatureFlagStore: LocalFeatureFlagStore,
    private val objectMapper: ObjectMapper,
    private val meterRegistry: MeterRegistry,
) : MessageListener {

    private val logger = LoggerFactory.getLogger(FeatureFlagChangeSubscriber::class.java)

    override fun onMessage(message: Message, pattern: ByteArray?) {
        val change = runCatching {
            objectMapper.readValue(message.body, FeatureFlagChangeMessage::class.java)
        }.getOrElse { exception ->
            logger.warn("event=feature-flag-change-message-invalid source=feature-flag message={}", exception.message)
            return
        }

        localFeatureFlagStore.refresh(change.flagKey)
        recordPropagationLag(change.occurredAt)
    }

    private fun recordPropagationLag(occurredAt: ZonedDateTime) {
        val lag = Duration.between(occurredAt, ZonedDateTime.now())
        Timer.builder(FeatureFlagCacheMetrics.PROPAGATION_LAG_TIMER)
            .register(meterRegistry)
            .record(lag)
    }
}

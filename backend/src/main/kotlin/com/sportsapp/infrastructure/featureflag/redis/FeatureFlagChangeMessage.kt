package com.sportsapp.infrastructure.featureflag.redis

import java.time.ZonedDateTime

/**
 * `featureflag:changes` 채널 메시지 계약 (`docs/feature-flag-redis-contract.md` §2).
 *
 * `occurredAt`은 전파 지연(occurredAt → 수신 시각) 측정에 쓰인다.
 */
data class FeatureFlagChangeMessage(
    val flagKey: String,
    val occurredAt: ZonedDateTime,
)

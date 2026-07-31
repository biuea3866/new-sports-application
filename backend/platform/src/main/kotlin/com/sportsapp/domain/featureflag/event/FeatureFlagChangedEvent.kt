package com.sportsapp.domain.featureflag.event

import com.sportsapp.domain.common.AbstractDomainEvent

/**
 * 플래그 변경(전략 수정·archive·activate) 시 등록되는 도메인 이벤트.
 *
 * topic=null → [com.sportsapp.infrastructure.messaging.RoutingDomainEventPublisher]가
 * Spring 내부 이벤트로만 발행한다. `FeatureFlagChangedEventListener`(presentation, 후속 티켓)가
 * AFTER_COMMIT에 수신해 캐시 갱신 + pub/sub 브로드캐스트를 트리거한다.
 */
class FeatureFlagChangedEvent(
    aggregateId: Long,
    val flagKey: String,
) : AbstractDomainEvent(aggregateId = aggregateId, topic = null)

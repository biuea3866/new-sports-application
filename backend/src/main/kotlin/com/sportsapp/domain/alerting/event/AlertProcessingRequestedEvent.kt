package com.sportsapp.domain.alerting.event

import com.sportsapp.domain.common.AbstractDomainEvent

/**
 * [Alert][com.sportsapp.domain.alerting.entity.Alert]가 RAISED로 저장된 직후 적재되는 이벤트.
 * presentation의 ProcessingEventWorker(AFTER_COMMIT @Async)가 소비해 LLM 분석을 트리거한다.
 */
class AlertProcessingRequestedEvent(
    alertId: Long,
) : AbstractDomainEvent(aggregateId = alertId)

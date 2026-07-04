package com.sportsapp.domain.alerting.event

import com.sportsapp.domain.alerting.vo.AlertSeverity
import com.sportsapp.domain.alerting.vo.AlertSource
import com.sportsapp.domain.common.AbstractDomainEvent

/**
 * 발송 준비 완료 이벤트 — [title]·[body]에 발송 문구를 비정규화해 담는다(DTO 흐름: TDD.md §DTO 흐름).
 * presentation의 DeliveryEventWorker(AFTER_COMMIT @Async)가 소비해 notification
 * `SendNotificationUseCase`(DISCORD 채널)를 호출한다. [Alert.attachAnalysis]와
 * [AlertDomainService.selfCheck][com.sportsapp.domain.alerting.service.AlertDomainService.selfCheck]
 * 양쪽에서 발행한다.
 */
class AlertDeliveryReadyEvent(
    alertId: Long,
    val title: String,
    val body: String,
    val source: AlertSource,
    val severity: AlertSeverity,
    val env: String,
) : AbstractDomainEvent(aggregateId = alertId)

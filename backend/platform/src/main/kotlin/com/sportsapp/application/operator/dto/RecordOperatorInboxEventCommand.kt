package com.sportsapp.application.operator.dto

import com.sportsapp.domain.operator.vo.OperatorInboxNotificationType

/**
 * 도메인 이벤트로부터 운영 인박스에 사건을 적재하는 파라미터.
 *
 * [eventId]는 멱등 키다 — Kafka는 at-least-once라 같은 이벤트를 두 번 받는 것이 정상이며,
 * 이 값이 없으면 파트너 인박스에 같은 알림이 중복으로 쌓이고 안읽음 배지도 부풀어 오른다.
 */
data class RecordOperatorInboxEventCommand(
    val eventId: String,
    val recipientUserId: Long,
    val type: OperatorInboxNotificationType,
    val title: String,
    val body: String,
    val link: String?,
)

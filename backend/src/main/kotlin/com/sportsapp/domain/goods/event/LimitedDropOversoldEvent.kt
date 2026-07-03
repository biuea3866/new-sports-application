package com.sportsapp.domain.goods.event

import com.sportsapp.domain.common.AbstractDomainEvent

/**
 * 한정판 회차 오버셀(초과 판매) 감지 이벤트.
 *
 * 리컨실리에이션(Redis remaining ↔ DB deducted 대사) 드리프트 감지 시
 * [com.sportsapp.domain.goods.entity.LimitedDrop.recordOversold]를 통해 적재된다.
 * [source]/[severity]는 지능형 장애 알림(⑥ 연계) 라우팅 태그 — 알림 subsystem이
 * 이 필드로 critical 알림 경로를 구분한다.
 */
class LimitedDropOversoldEvent(
    val dropId: Long,
    val productId: Long,
    val detectedQuantity: Int,
    val source: String = "oversell",
    val severity: String = "critical",
) : AbstractDomainEvent(aggregateId = dropId)

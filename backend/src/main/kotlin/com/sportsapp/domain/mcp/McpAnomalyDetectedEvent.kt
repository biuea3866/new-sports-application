package com.sportsapp.domain.mcp

import com.sportsapp.domain.common.AbstractDomainEvent

/**
 * MCP 비정상 패턴 탐지 이벤트.
 *
 * [tokenId] 에 해당하는 토큰이 7일 베이스라인 대비 이상 급증을 보일 때 발행된다.
 * topic = null → Spring 내부 이벤트로만 발행 (Kafka 외부 발행은 MVP 범위 외).
 */
class McpAnomalyDetectedEvent(
    val tokenId: Long,
    val userId: Long,
    val currentHourCount: Long,
    val baselineAverage: Double,
) : AbstractDomainEvent(aggregateId = tokenId, topic = null)

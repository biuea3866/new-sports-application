package com.sportsapp.application.mcp

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

data class GetMcpUsageAnalyticsCommand(
    val userId: Long,
    val from: ZonedDateTime,
    val to: ZonedDateTime,
) {
    init {
        require(from.isBefore(to)) { "from은 to보다 이전이어야 합니다" }
        require(ChronoUnit.DAYS.between(from, to) <= 365L) { "조회 기간은 최대 365일입니다" }
    }
}

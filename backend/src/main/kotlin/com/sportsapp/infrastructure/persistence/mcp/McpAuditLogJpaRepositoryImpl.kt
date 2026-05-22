package com.sportsapp.infrastructure.persistence.mcp

import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.mcp.DailyCallCount
import com.sportsapp.domain.mcp.QMcpAuditLog.mcpAuditLog
import com.sportsapp.domain.mcp.TokenCallStats
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.time.ZonedDateTime

class McpAuditLogJpaRepositoryImpl : McpAuditLogQueryDslRepository {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val queryFactory: JPAQueryFactory
        get() = JPAQueryFactory(entityManager)

    override fun findCallStatsByTokenIdIn(
        tokenIds: List<Long>,
        from: ZonedDateTime,
    ): List<TokenCallStats> {
        if (tokenIds.isEmpty()) return emptyList()

        return queryFactory.select(
            Projections.constructor(
                TokenCallStats::class.java,
                mcpAuditLog.tokenId,
                mcpAuditLog.id.count(),
            )
        )
            .from(mcpAuditLog)
            .where(
                mcpAuditLog.tokenId.`in`(tokenIds),
                mcpAuditLog.calledAt.goe(from),
            )
            .groupBy(mcpAuditLog.tokenId)
            .fetch()
    }

    override fun findDailyCallCountsForBaseline(
        tokenId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<DailyCallCount> {
        return queryFactory.select(
            Projections.constructor(
                DailyCallCount::class.java,
                mcpAuditLog.calledAt.dayOfMonth(),
                mcpAuditLog.id.count(),
            )
        )
            .from(mcpAuditLog)
            .where(
                mcpAuditLog.tokenId.eq(tokenId),
                mcpAuditLog.calledAt.goe(from),
                mcpAuditLog.calledAt.lt(to),
            )
            .groupBy(mcpAuditLog.calledAt.dayOfMonth())
            .fetch()
    }

    override fun findCurrentHourCallCount(
        tokenId: Long,
        from: ZonedDateTime,
    ): Long {
        return queryFactory.select(mcpAuditLog.id.count())
                           .from(mcpAuditLog)
                           .where(
                               mcpAuditLog.tokenId.eq(tokenId),
                               mcpAuditLog.calledAt.goe(from),
                           )
                           .fetchOne() ?: 0L
    }
}

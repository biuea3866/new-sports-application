package com.sportsapp.infrastructure.mcp.mysql

import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.mcp.dto.DailyCallCount
import com.sportsapp.domain.mcp.dto.DailyUsageStat
import com.sportsapp.domain.mcp.dto.ErrorRateStat
import com.sportsapp.domain.mcp.entity.QMcpAuditLog.mcpAuditLog
import com.sportsapp.domain.mcp.repository.TokenCallStats
import com.sportsapp.domain.mcp.dto.TokenUsageStat
import com.sportsapp.domain.mcp.dto.ToolCallStat
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

    override fun findDailyUsageStats(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<DailyUsageStat> {
        val tuples = queryFactory.select(
            mcpAuditLog.calledAt.year(),
            mcpAuditLog.calledAt.month(),
            mcpAuditLog.calledAt.dayOfMonth(),
            mcpAuditLog.toolName,
            mcpAuditLog.id.count(),
        )
            .from(mcpAuditLog)
            .where(
                mcpAuditLog.userId.eq(userId),
                mcpAuditLog.calledAt.goe(from),
                mcpAuditLog.calledAt.lt(to),
            )
            .groupBy(
                mcpAuditLog.calledAt.year(),
                mcpAuditLog.calledAt.month(),
                mcpAuditLog.calledAt.dayOfMonth(),
                mcpAuditLog.toolName,
            )
            .orderBy(
                mcpAuditLog.calledAt.year().asc(),
                mcpAuditLog.calledAt.month().asc(),
                mcpAuditLog.calledAt.dayOfMonth().asc(),
            )
            .fetch()
        return tuples.map { toDailyUsageStat(it) }
    }

    private fun toDailyUsageStat(tuple: com.querydsl.core.Tuple): DailyUsageStat {
        val year = requireNotNull(tuple.get(mcpAuditLog.calledAt.year()))
        val month = requireNotNull(tuple.get(mcpAuditLog.calledAt.month()))
        val day = requireNotNull(tuple.get(mcpAuditLog.calledAt.dayOfMonth()))
        return DailyUsageStat(
            date = "%04d-%02d-%02d".format(year, month, day),
            toolName = requireNotNull(tuple.get(mcpAuditLog.toolName)),
            callCount = tuple.get(mcpAuditLog.id.count()) ?: 0L,
        )
    }

    override fun findToolCallStats(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<ToolCallStat> {
        return queryFactory.select(
            Projections.constructor(
                ToolCallStat::class.java,
                mcpAuditLog.toolName,
                mcpAuditLog.id.count(),
            )
        )
            .from(mcpAuditLog)
            .where(
                mcpAuditLog.userId.eq(userId),
                mcpAuditLog.calledAt.goe(from),
                mcpAuditLog.calledAt.lt(to),
            )
            .groupBy(mcpAuditLog.toolName)
            .orderBy(mcpAuditLog.id.count().desc())
            .fetch()
    }

    override fun findErrorRateStat(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): ErrorRateStat {
        val totalCount = queryFactory.select(mcpAuditLog.id.count())
                                     .from(mcpAuditLog)
                                     .where(
                                         mcpAuditLog.userId.eq(userId),
                                         mcpAuditLog.calledAt.goe(from),
                                         mcpAuditLog.calledAt.lt(to),
                                     )
                                     .fetchOne() ?: 0L

        val errorCount = queryFactory.select(mcpAuditLog.id.count())
                                     .from(mcpAuditLog)
                                     .where(
                                         mcpAuditLog.userId.eq(userId),
                                         mcpAuditLog.calledAt.goe(from),
                                         mcpAuditLog.calledAt.lt(to),
                                         mcpAuditLog.statusCode.goe(400),
                                     )
                                     .fetchOne() ?: 0L

        return ErrorRateStat(totalCount = totalCount, errorCount = errorCount)
    }

    override fun findLatencyMsByTool(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): Map<String, List<Int>> {
        val tuples = queryFactory.select(
            mcpAuditLog.toolName,
            mcpAuditLog.latencyMs,
        )
            .from(mcpAuditLog)
            .where(
                mcpAuditLog.userId.eq(userId),
                mcpAuditLog.calledAt.goe(from),
                mcpAuditLog.calledAt.lt(to),
            )
            .limit(100_000L)
            .fetch()

        return tuples.groupBy(
            keySelector = { requireNotNull(it.get(mcpAuditLog.toolName)) },
            valueTransform = { it.get(mcpAuditLog.latencyMs) ?: 0 },
        )
    }

    override fun findTokenUsageStats(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
        limit: Int,
    ): List<TokenUsageStat> {
        val tuples = fetchTokenTuples(userId, from, to, limit)
        val tokenIds = tuples.mapNotNull { it.get(mcpAuditLog.tokenId) }
        if (tokenIds.isEmpty()) return emptyList()
        val errorCountsByToken = fetchErrorCountsByToken(userId, from, to, tokenIds)
        return tuples.mapNotNull { tuple ->
            val tokenId = tuple.get(mcpAuditLog.tokenId) ?: return@mapNotNull null
            TokenUsageStat(
                tokenId = tokenId,
                callCount = tuple.get(mcpAuditLog.id.count()) ?: 0L,
                errorCount = errorCountsByToken.getOrDefault(tokenId, 0L),
                lastCalledAt = tuple.get(mcpAuditLog.calledAt.max()),
            )
        }
    }

    private fun fetchTokenTuples(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
        limit: Int,
    ): List<com.querydsl.core.Tuple> = queryFactory.select(
        mcpAuditLog.tokenId,
        mcpAuditLog.id.count(),
        mcpAuditLog.calledAt.max(),
    )
        .from(mcpAuditLog)
        .where(
            mcpAuditLog.userId.eq(userId),
            mcpAuditLog.calledAt.goe(from),
            mcpAuditLog.calledAt.lt(to),
            mcpAuditLog.tokenId.isNotNull,
        )
        .groupBy(mcpAuditLog.tokenId)
        .orderBy(mcpAuditLog.id.count().desc())
        .limit(limit.toLong())
        .fetch()

    private fun fetchErrorCountsByToken(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
        tokenIds: List<Long>,
    ): Map<Long, Long> = queryFactory.select(
        Projections.constructor(
            TokenCallStats::class.java,
            mcpAuditLog.tokenId,
            mcpAuditLog.id.count(),
        )
    )
        .from(mcpAuditLog)
        .where(
            mcpAuditLog.userId.eq(userId),
            mcpAuditLog.calledAt.goe(from),
            mcpAuditLog.calledAt.lt(to),
            mcpAuditLog.tokenId.`in`(tokenIds),
            mcpAuditLog.statusCode.goe(400),
        )
        .groupBy(mcpAuditLog.tokenId)
        .fetch()
        .associate { it.tokenId to it.callCount }
}

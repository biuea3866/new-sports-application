package com.sportsapp.infrastructure.mcp.mysql

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.mcp.entity.McpToken
import com.sportsapp.domain.mcp.entity.McpTokenStatus
import com.sportsapp.domain.mcp.entity.QMcpToken.mcpToken
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext

class McpTokenJpaRepositoryImpl : McpTokenQueryDslRepository {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val queryFactory: JPAQueryFactory by lazy { JPAQueryFactory(entityManager) }

    override fun findActiveByUserId(userId: Long): List<McpToken> {
        return queryFactory.selectFrom(mcpToken)
                           .where(
                               mcpToken.userId.eq(userId),
                               mcpToken.status.eq(McpTokenStatus.ACTIVE),
                               mcpToken.deletedAt.isNull,
                           )
                           .fetch()
    }

    override fun findAllActive(): List<McpToken> {
        return queryFactory.selectFrom(mcpToken)
                           .where(
                               mcpToken.status.eq(McpTokenStatus.ACTIVE),
                               mcpToken.deletedAt.isNull,
                           )
                           .fetch()
    }
}

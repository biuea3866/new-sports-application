package com.sportsapp.infrastructure.persistence.user

import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.user.QRole.role
import com.sportsapp.domain.user.QUser.user
import com.sportsapp.domain.user.QUserRole.userRole
import com.sportsapp.domain.user.UserCustomRepository
import com.sportsapp.domain.user.UserWithRoles
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class UserCustomRepositoryImpl : UserCustomRepository {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val queryFactory: JPAQueryFactory
        get() = JPAQueryFactory(entityManager)

    override fun findAllWithRoles(
        emailKeyword: String?,
        roleName: String?,
        pageable: Pageable,
    ): Page<UserWithRoles> {
        val userIds = fetchFilteredUserIds(emailKeyword, roleName, pageable)
        val total = fetchTotalCount(emailKeyword, roleName)

        if (userIds.isEmpty()) {
            return PageImpl(emptyList(), pageable, total)
        }

        val rows = queryFactory.select(user, role.name)
                               .from(user)
                               .leftJoin(userRole).on(userRole.userId.eq(user.id).and(userRole.deletedAt.isNull))
                               .leftJoin(role).on(role.id.eq(userRole.roleId).and(role.deletedAt.isNull))
                               .where(user.id.`in`(userIds).and(user.deletedAt.isNull))
                               .fetch()

        val grouped = rows.groupBy { tuple ->
            requireNotNull(tuple.get(user)) { "user must not be null" }
        }

        val content = userIds.mapNotNull { userId ->
            grouped.entries.find { entry -> entry.key.id == userId }?.let { (userEntity, tuples) ->
                UserWithRoles(
                    userId = userEntity.id,
                    email = userEntity.email,
                    status = userEntity.status,
                    roleNames = tuples.mapNotNull { tuple -> tuple.get(role.name) },
                    joinedAt = userEntity.createdAt,
                )
            }
        }

        return PageImpl(content, pageable, total)
    }

    private fun fetchFilteredUserIds(
        emailKeyword: String?,
        roleName: String?,
        pageable: Pageable,
    ): List<Long> {
        val condition = buildCondition(emailKeyword, roleName)
        val orderSpecifier = buildOrderSpecifier(pageable)

        return queryFactory.select(user.id)
                           .from(user)
                           .leftJoin(userRole).on(userRole.userId.eq(user.id).and(userRole.deletedAt.isNull))
                           .leftJoin(role).on(role.id.eq(userRole.roleId).and(role.deletedAt.isNull))
                           .where(condition)
                           .groupBy(user.id, user.createdAt)
                           .orderBy(orderSpecifier)
                           .offset(pageable.offset)
                           .limit(pageable.pageSize.toLong())
                           .fetch()
    }

    private fun fetchTotalCount(emailKeyword: String?, roleName: String?): Long {
        val condition = buildCondition(emailKeyword, roleName)
        return queryFactory.select(user.id.countDistinct())
                           .from(user)
                           .leftJoin(userRole).on(userRole.userId.eq(user.id).and(userRole.deletedAt.isNull))
                           .leftJoin(role).on(role.id.eq(userRole.roleId).and(role.deletedAt.isNull))
                           .where(condition)
                           .fetchOne() ?: 0L
    }

    private fun buildCondition(emailKeyword: String?, roleName: String?): BooleanBuilder {
        val condition = BooleanBuilder()
        condition.and(user.deletedAt.isNull)
        emailKeyword?.takeIf { it.isNotBlank() }
            ?.let { condition.and(user.email.containsIgnoreCase(it)) }
        roleName?.takeIf { it.isNotBlank() }
            ?.let { condition.and(role.name.eq(it)).and(userRole.deletedAt.isNull) }
        return condition
    }

    private fun buildOrderSpecifier(pageable: Pageable): OrderSpecifier<*> {
        val order = pageable.sort.iterator().takeIf { it.hasNext() }?.next()
        return if (order != null && order.property == "createdAt" && order.isAscending) {
            user.createdAt.asc()
        } else {
            user.createdAt.desc()
        }
    }
}

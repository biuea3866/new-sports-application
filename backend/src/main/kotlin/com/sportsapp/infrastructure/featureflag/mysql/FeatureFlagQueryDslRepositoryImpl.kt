package com.sportsapp.infrastructure.featureflag.mysql

import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.featureflag.entity.FeatureFlag
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.entity.QFeatureFlag.featureFlag
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime

@Repository
class FeatureFlagQueryDslRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : FeatureFlagQueryDslRepository {

    override fun findAllActive(): List<FeatureFlag> =
        queryFactory.selectFrom(featureFlag)
            .where(featureFlag.status.eq(FeatureFlagStatus.ACTIVE))
            .fetch()

    override fun findAll(status: FeatureFlagStatus?, type: FeatureFlagType?): List<FeatureFlag> {
        val condition = BooleanBuilder()
        status?.let { condition.and(featureFlag.status.eq(it)) }
        type?.let { condition.and(featureFlag.type.eq(it)) }

        return queryFactory.selectFrom(featureFlag)
            .where(condition)
            .fetch()
    }

    override fun findStale(status: FeatureFlagStatus, type: FeatureFlagType, updatedBefore: ZonedDateTime): List<FeatureFlag> =
        queryFactory.selectFrom(featureFlag)
            .where(
                featureFlag.status.eq(status),
                featureFlag.type.eq(type),
                featureFlag.updatedAt.before(updatedBefore),
            )
            .fetch()
}

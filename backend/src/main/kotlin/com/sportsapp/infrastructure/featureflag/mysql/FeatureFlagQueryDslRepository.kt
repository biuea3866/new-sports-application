package com.sportsapp.infrastructure.featureflag.mysql

import com.sportsapp.domain.featureflag.entity.FeatureFlag
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import java.time.ZonedDateTime

interface FeatureFlagQueryDslRepository {
    fun findAllActive(): List<FeatureFlag>
    fun findAll(status: FeatureFlagStatus?, type: FeatureFlagType?): List<FeatureFlag>
    fun findStale(status: FeatureFlagStatus, type: FeatureFlagType, updatedBefore: ZonedDateTime): List<FeatureFlag>
}

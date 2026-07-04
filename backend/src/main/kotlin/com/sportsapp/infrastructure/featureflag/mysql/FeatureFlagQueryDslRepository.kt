package com.sportsapp.infrastructure.featureflag.mysql

import com.sportsapp.domain.featureflag.entity.FeatureFlag
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType

interface FeatureFlagQueryDslRepository {
    fun findAllActive(): List<FeatureFlag>
    fun findAll(status: FeatureFlagStatus?, type: FeatureFlagType?): List<FeatureFlag>
}

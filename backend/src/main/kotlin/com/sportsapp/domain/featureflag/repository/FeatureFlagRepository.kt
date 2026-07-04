package com.sportsapp.domain.featureflag.repository

import com.sportsapp.domain.featureflag.entity.FeatureFlag
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType

interface FeatureFlagRepository {
    fun save(featureFlag: FeatureFlag): FeatureFlag
    fun findByKey(key: String): FeatureFlag?
    fun findById(id: Long): FeatureFlag?
    fun findAllActive(): List<FeatureFlag>
    fun findAll(status: FeatureFlagStatus?, type: FeatureFlagType?): List<FeatureFlag>
    fun existsByKey(key: String): Boolean
}

package com.sportsapp.infrastructure.featureflag.mysql

import com.sportsapp.domain.featureflag.entity.FeatureFlag
import org.springframework.data.jpa.repository.JpaRepository

interface FeatureFlagJpaRepository : JpaRepository<FeatureFlag, Long>, FeatureFlagQueryDslRepository {
    fun findByFlagKey(flagKey: String): FeatureFlag?
    fun existsByFlagKey(flagKey: String): Boolean
}

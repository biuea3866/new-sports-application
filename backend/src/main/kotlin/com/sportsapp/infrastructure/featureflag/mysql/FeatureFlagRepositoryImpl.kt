package com.sportsapp.infrastructure.featureflag.mysql

import com.sportsapp.domain.featureflag.entity.FeatureFlag
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.repository.FeatureFlagRepository
import org.springframework.stereotype.Repository

@Repository
class FeatureFlagRepositoryImpl(
    private val featureFlagJpaRepository: FeatureFlagJpaRepository,
) : FeatureFlagRepository {

    override fun save(featureFlag: FeatureFlag): FeatureFlag =
        featureFlagJpaRepository.save(featureFlag)

    override fun findByKey(key: String): FeatureFlag? =
        featureFlagJpaRepository.findByFlagKey(key)

    override fun findById(id: Long): FeatureFlag? =
        featureFlagJpaRepository.findById(id).orElse(null)

    override fun findAllActive(): List<FeatureFlag> =
        featureFlagJpaRepository.findAllActive()

    override fun findAll(status: FeatureFlagStatus?, type: FeatureFlagType?): List<FeatureFlag> =
        featureFlagJpaRepository.findAll(status, type)

    override fun existsByKey(key: String): Boolean =
        featureFlagJpaRepository.existsByFlagKey(key)
}

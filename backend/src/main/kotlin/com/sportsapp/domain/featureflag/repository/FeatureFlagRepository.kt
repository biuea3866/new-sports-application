package com.sportsapp.domain.featureflag.repository

import com.sportsapp.domain.featureflag.entity.FeatureFlag
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import java.time.ZonedDateTime

interface FeatureFlagRepository {
    fun save(featureFlag: FeatureFlag): FeatureFlag
    fun findByKey(key: String): FeatureFlag?
    fun findById(id: Long): FeatureFlag?
    fun findAllActive(): List<FeatureFlag>
    fun findAll(status: FeatureFlagStatus?, type: FeatureFlagType?): List<FeatureFlag>
    fun existsByKey(key: String): Boolean

    /**
     * 정리 후보 탐지(FR-14) — `status`·`type`이고 `updatedAt`이 `updatedBefore`보다 이전인 플래그를 조회한다.
     */
    fun findStale(status: FeatureFlagStatus, type: FeatureFlagType, updatedBefore: ZonedDateTime): List<FeatureFlag>
}

package com.sportsapp.domain.featureflag.entity

/**
 * FeatureFlag 변경 감사 이력의 변경 종류.
 */
enum class FeatureFlagChangeType {
    CREATED,
    UPDATED,
    ARCHIVED,
    ACTIVATED,
}

package com.sportsapp.domain.featureflag.dto

import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType

data class ListFeatureFlagsCommand(
    val status: FeatureFlagStatus?,
    val type: FeatureFlagType?,
)

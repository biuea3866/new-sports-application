package com.sportsapp.domain.featuredemo.service

import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.featuredemo.exception.FeatureDisabledException
import com.sportsapp.domain.featuredemo.vo.Greeting
import org.springframework.stereotype.Service

private const val DEMO_FLAG_KEY = "demo.feature.hello"

/**
 * 데모 게이팅(BE-09) 도메인 로직.
 *
 * `common.FeatureFlagEvaluator`·`FeatureContext`만 주입한다 — `domain.featureflag`를 import하지
 * 않아 소비 도메인의 도메인 격리를 스스로 증명한다 (정적 검증: [FeatureDemoDomainIsolationTest]).
 */
@Service
class FeatureDemoDomainService(
    private val featureFlagEvaluator: FeatureFlagEvaluator,
) {
    fun greet(userId: Long?): Greeting {
        val context = FeatureContext.of(userId)
        val enabled = featureFlagEvaluator.isEnabled(DEMO_FLAG_KEY, context, false)
        if (!enabled) throw FeatureDisabledException(DEMO_FLAG_KEY)
        return Greeting.of(DEMO_FLAG_KEY)
    }
}

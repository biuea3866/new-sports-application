package com.sportsapp.application.featuredemo.dto

import com.sportsapp.domain.featuredemo.vo.Greeting
import java.time.ZonedDateTime

/**
 * [com.sportsapp.presentation.featuredemo.controller.FeatureDemoApiController]가 그대로 반환하는
 * UseCase 결과 — `{message, flagKey, userId, servedAt}` API 계약.
 */
data class DemoGreetingResponse(
    val message: String,
    val flagKey: String,
    val userId: Long?,
    val servedAt: ZonedDateTime,
) {
    companion object {
        fun of(greeting: Greeting, userId: Long?): DemoGreetingResponse = DemoGreetingResponse(
            message = greeting.message,
            flagKey = greeting.flagKey,
            userId = userId,
            servedAt = greeting.servedAt,
        )
    }
}

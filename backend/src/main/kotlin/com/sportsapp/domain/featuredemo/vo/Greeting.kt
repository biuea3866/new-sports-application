package com.sportsapp.domain.featuredemo.vo

import java.time.ZonedDateTime

/**
 * 데모 게이팅(BE-09) 응답 값 객체.
 *
 * 부작용 없는 고정 인사 메시지 — 킬스위치·퍼센티지 sticky 증명이 목적이라 데이터 자체는 의미가 없다.
 */
data class Greeting(
    val message: String,
    val flagKey: String,
    val servedAt: ZonedDateTime,
) {
    companion object {
        private const val GREETING_MESSAGE = "Hello from the feature-flagged demo endpoint"

        fun of(flagKey: String): Greeting = Greeting(
            message = GREETING_MESSAGE,
            flagKey = flagKey,
            servedAt = ZonedDateTime.now(),
        )
    }
}

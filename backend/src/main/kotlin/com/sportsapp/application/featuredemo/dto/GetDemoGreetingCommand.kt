package com.sportsapp.application.featuredemo.dto

/**
 * `X-User-Id` 헤더값(optional)을 담아 [com.sportsapp.domain.featuredemo.service.FeatureDemoDomainService]에
 * 전달하는 실행 파라미터. 헤더가 없으면 `userId`는 null이며 퍼센티지 롤아웃 등 사용자 식별 기반 전략은
 * default(false)로 평가된다.
 */
data class GetDemoGreetingCommand(
    val userId: Long?,
)

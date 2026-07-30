package com.sportsapp.presentation.featuredemo.controller

import com.sportsapp.application.featuredemo.dto.DemoGreetingResponse
import com.sportsapp.application.featuredemo.dto.GetDemoGreetingCommand
import com.sportsapp.application.featuredemo.usecase.GetDemoGreetingUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 데모 게이팅(BE-09) 엔드포인트 — `demo.feature.hello` 플래그로 게이팅한다.
 *
 * `feature-demo` 하위 경로 전체의 permitAll 시큐리티 배선은 BE-10 담당 (SecurityConfig 미수정).
 */
@RestController
@RequestMapping("/feature-demo")
class FeatureDemoApiController(
    private val getDemoGreetingUseCase: GetDemoGreetingUseCase,
) {
    @GetMapping("/hello")
    fun getHello(
        @RequestHeader("X-User-Id", required = false) userId: Long?,
    ): ResponseEntity<DemoGreetingResponse> {
        val response = getDemoGreetingUseCase.execute(GetDemoGreetingCommand(userId = userId))
        return ResponseEntity.ok(response)
    }
}

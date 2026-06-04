package com.sportsapp.presentation.user.controller

import com.sportsapp.application.user.dto.LogoutCommand
import com.sportsapp.application.user.usecase.LoginUseCase
import com.sportsapp.application.user.usecase.LogoutUseCase
import com.sportsapp.application.user.usecase.RefreshUseCase
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.user.dto.request.LoginRequest
import com.sportsapp.presentation.user.dto.request.RefreshRequest
import com.sportsapp.presentation.user.dto.response.LoginResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthApiController(
    private val loginUseCase: LoginUseCase,
    private val refreshUseCase: RefreshUseCase,
    private val logoutUseCase: LogoutUseCase,
) {
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        val tokenPair = loginUseCase.execute(request.toCommand())
        return ResponseEntity.ok(LoginResponse.of(tokenPair))
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<LoginResponse> {
        val tokenPair = refreshUseCase.execute(request.toCommand())
        return ResponseEntity.ok(LoginResponse.of(tokenPair))
    }

    @PostMapping("/logout")
    fun logout(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestHeader("Authorization") bearerToken: String,
    ): ResponseEntity<Void> {
        val accessToken = bearerToken.removePrefix("Bearer ")
        logoutUseCase.execute(LogoutCommand(accessToken = accessToken, userId = principal.id))
        return ResponseEntity.noContent().build()
    }
}

package com.sportsapp.presentation.auth

import com.sportsapp.application.user.LoginResponse
import com.sportsapp.application.user.LoginUseCase
import com.sportsapp.application.user.LogoutCommand
import com.sportsapp.application.user.LogoutUseCase
import com.sportsapp.application.user.RefreshUseCase
import com.sportsapp.domain.user.UserPrincipal
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
        val response = loginUseCase.execute(request.toCommand())
        return ResponseEntity.ok(response)
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<LoginResponse> {
        val response = refreshUseCase.execute(request.toCommand())
        return ResponseEntity.ok(response)
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

package com.sportsapp.presentation.auth

import com.sportsapp.application.user.LoginResponse
import com.sportsapp.application.user.LoginUseCase
import com.sportsapp.application.user.RefreshUseCase
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthApiController(
    private val loginUseCase: LoginUseCase,
    private val refreshUseCase: RefreshUseCase,
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
}

package com.sportsapp.presentation.user

import com.sportsapp.application.user.RegisterUserResponse
import com.sportsapp.application.user.RegisterUserUseCase
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/users")
class UserApiController(
    private val registerUserUseCase: RegisterUserUseCase,
) {
    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterUserRequest): ResponseEntity<RegisterUserResponse> {
        val response = registerUserUseCase.execute(request.toCommand())
        return ResponseEntity.created(URI.create("/users/${response.id}")).body(response)
    }
}

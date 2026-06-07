package com.sportsapp.presentation.user.controller

import com.sportsapp.application.user.dto.GetMyProfileCommand
import com.sportsapp.application.user.usecase.GetMyProfileUseCase
import com.sportsapp.application.user.usecase.RegisterUserUseCase
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.security.CurrentUser
import com.sportsapp.presentation.user.dto.request.RegisterUserRequest
import com.sportsapp.presentation.user.dto.response.GetMyProfileResponse
import com.sportsapp.presentation.user.dto.response.RegisterUserResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/users")
class UserApiController(
    private val registerUserUseCase: RegisterUserUseCase,
    private val getMyProfileUseCase: GetMyProfileUseCase,
) {
    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterUserRequest): ResponseEntity<RegisterUserResponse> {
        val user = registerUserUseCase.execute(request.toCommand())
        val response = RegisterUserResponse.of(user)
        return ResponseEntity.created(URI.create("/users/${response.id}")).body(response)
    }

    @GetMapping("/me")
    fun getMyProfile(@CurrentUser principal: UserPrincipal): ResponseEntity<GetMyProfileResponse> {
        val user = getMyProfileUseCase.execute(GetMyProfileCommand(userId = principal.id))
        return ResponseEntity.ok(GetMyProfileResponse.of(user))
    }
}

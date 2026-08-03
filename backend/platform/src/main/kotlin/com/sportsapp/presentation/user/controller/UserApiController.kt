package com.sportsapp.presentation.user.controller

import com.sportsapp.application.user.dto.ChangeMyNicknameCommand
import com.sportsapp.application.user.dto.GetMyProfileCommand
import com.sportsapp.application.user.dto.GetMyProfileResponse
import com.sportsapp.application.user.dto.RegisterUserResponse
import com.sportsapp.application.user.usecase.ChangeMyNicknameUseCase
import com.sportsapp.application.user.usecase.GetMyProfileUseCase
import com.sportsapp.application.user.usecase.RegisterUserUseCase
import com.sportsapp.domain.common.security.UserPrincipal
import com.sportsapp.presentation.security.CurrentUser
import com.sportsapp.presentation.user.dto.request.ChangeMyNicknameRequest
import com.sportsapp.presentation.user.dto.request.RegisterUserRequest
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
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
    private val changeMyNicknameUseCase: ChangeMyNicknameUseCase,
) {
    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterUserRequest): ResponseEntity<RegisterUserResponse> {
        val response = registerUserUseCase.execute(request.toCommand())
        return ResponseEntity.created(URI.create("/users/${response.id}")).body(response)
    }

    @GetMapping("/me")
    fun getMyProfile(@CurrentUser principal: UserPrincipal): ResponseEntity<GetMyProfileResponse> {
        return ResponseEntity.ok(getMyProfileUseCase.execute(GetMyProfileCommand(userId = principal.id)))
    }

    @PatchMapping("/me/nickname")
    fun changeMyNickname(
        @CurrentUser principal: UserPrincipal,
        @Valid @RequestBody request: ChangeMyNicknameRequest,
    ): ResponseEntity<GetMyProfileResponse> {
        return ResponseEntity.ok(changeMyNicknameUseCase.execute(request.toCommand(principal.id)))
    }
}

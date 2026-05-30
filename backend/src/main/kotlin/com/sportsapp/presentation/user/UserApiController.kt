package com.sportsapp.presentation.user

import com.sportsapp.application.user.GetMyProfileCommand
import com.sportsapp.application.user.GetMyProfileResponse
import com.sportsapp.application.user.GetMyProfileUseCase
import com.sportsapp.application.user.RegisterUserResponse
import com.sportsapp.application.user.RegisterUserUseCase
import com.sportsapp.domain.user.UserPrincipal
import com.sportsapp.presentation.security.CurrentUser
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
        val response = registerUserUseCase.execute(request.toCommand())
        return ResponseEntity.created(URI.create("/users/${response.id}")).body(response)
    }

    @GetMapping("/me")
    fun getMyProfile(@CurrentUser principal: UserPrincipal): ResponseEntity<GetMyProfileResponse> {
        val response = getMyProfileUseCase.execute(GetMyProfileCommand(userId = principal.id))
        return ResponseEntity.ok(response)
    }
}

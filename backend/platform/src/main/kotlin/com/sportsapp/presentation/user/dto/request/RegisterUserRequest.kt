package com.sportsapp.presentation.user.dto.request

import com.sportsapp.application.user.dto.RegisterUserCommand
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterUserRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank @field:Size(min = 8) val password: String,
    // 길이·허용 문자 규칙은 User.changeNickname 이 단일 기준이라 여기서 중복 선언하지 않는다.
    @field:NotBlank val nickname: String,
) {
    fun toCommand(): RegisterUserCommand = RegisterUserCommand(
        email = email,
        rawPassword = password,
        nickname = nickname,
    )
}

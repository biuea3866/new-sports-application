package com.sportsapp.presentation.user.dto.request

import com.sportsapp.application.user.dto.ChangeMyNicknameCommand
import jakarta.validation.constraints.NotBlank

data class ChangeMyNicknameRequest(
    // 길이·허용 문자 규칙은 User.changeNickname 이 단일 기준이라 여기서 중복 선언하지 않는다.
    @field:NotBlank val nickname: String,
) {
    fun toCommand(userId: Long): ChangeMyNicknameCommand = ChangeMyNicknameCommand(
        userId = userId,
        nickname = nickname,
    )
}

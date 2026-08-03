package com.sportsapp.application.user.dto

import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.entity.UserStatus
import java.time.ZonedDateTime

data class GetMyProfileResponse(
    val id: Long,
    val email: String,
    /** 미설정이면 null — 클라이언트가 "닉네임을 설정해 주세요" 유도 UI 를 띄우는 근거다. */
    val nickname: String?,
    /** 타인에게 보이는 이름. 닉네임 미설정 계정은 중립 기본값이 들어간다. */
    val displayName: String,
    val status: UserStatus,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun of(user: User): GetMyProfileResponse = GetMyProfileResponse(
            id = user.id,
            email = user.email,
            nickname = user.nickname,
            displayName = user.displayName,
            status = user.status,
            createdAt = user.createdAt,
        )
    }
}

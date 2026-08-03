package com.sportsapp.domain.user.dto

import com.sportsapp.domain.user.entity.User

/**
 * 사용자 id → 표시 이름 조회 결과. 목록 화면(게시글·멤버·초대·신청자)이 사용자별 조회(N+1) 없이
 * 한 번에 이름을 받기 위한 값 객체다.
 *
 * 조회되지 않은 id(탈퇴·삭제 포함)의 폴백 판단을 호출부로 흘리지 않기 위해 Map 을 그대로 노출하지
 * 않고 [of] 만 노출한다 — 호출부에서 `names[id] ?: 기본값` 이 반복되는 것을 막는다.
 */
class UserDisplayNames private constructor(
    private val displayNamesByUserId: Map<Long, String>,
) {
    fun of(userId: Long): String = displayNamesByUserId[userId] ?: User.UNSET_NICKNAME_DISPLAY_NAME

    companion object {
        fun from(users: List<User>): UserDisplayNames =
            UserDisplayNames(users.associate { it.id to it.displayName })
    }
}

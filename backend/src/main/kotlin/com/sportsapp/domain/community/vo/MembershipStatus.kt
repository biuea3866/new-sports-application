package com.sportsapp.domain.community.vo

/**
 * 커뮤니티 멤버십 상태 (TDD Detail Design "상태 전이 표 — CommunityMember").
 * PENDING_APPROVAL → ACTIVE(승인), ACTIVE → LEFT(탈퇴)/KICKED(추방)만 허용하고
 * LEFT·KICKED는 종료(terminal) 상태로 재전이를 거부한다.
 */
enum class MembershipStatus {
    ACTIVE,
    PENDING_APPROVAL,
    LEFT,
    KICKED,
    ;

    /** 현재 상태에서 [next] 상태로 전이 가능한지 질의한다. */
    fun canTransitTo(next: MembershipStatus): Boolean = when (this) {
        PENDING_APPROVAL -> next == ACTIVE
        ACTIVE -> next == LEFT || next == KICKED
        LEFT, KICKED -> false
    }
}

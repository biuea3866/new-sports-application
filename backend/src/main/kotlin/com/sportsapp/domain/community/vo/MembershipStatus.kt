package com.sportsapp.domain.community.vo

/**
 * 커뮤니티 멤버십 상태 (TDD Detail Design "상태 전이 표 — CommunityMember").
 * PENDING_APPROVAL → ACTIVE(승인), ACTIVE → LEFT(탈퇴)/KICKED(추방)를 허용한다.
 * LEFT·KICKED는 강퇴·승인 관점에서는 종료 상태이지만, 재가입 시 ACTIVE(공개)
 * 또는 PENDING_APPROVAL(비공개)로 재활성화될 수 있다(`CommunityMember.rejoin`) —
 * `community_members` UNIQUE(community_id, user_id, deleted_at) 제약 하에서
 * kick/leave가 row를 soft-delete하지 않으므로, 재가입은 새 row INSERT가 아니라
 * 기존 row의 상태 전이로 처리해야 한다(리뷰 p2-①).
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
        LEFT, KICKED -> next == ACTIVE || next == PENDING_APPROVAL
    }
}

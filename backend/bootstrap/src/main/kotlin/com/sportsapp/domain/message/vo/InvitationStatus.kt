package com.sportsapp.domain.message.vo

/**
 * 게스트 초대(RoomInvitation)의 상태 (TDD Detail Design "상태 전이 표 — RoomInvitation").
 * PENDING에서만 다른 상태로 전이할 수 있고, 그 외 상태는 모두 종료(terminal) 상태다.
 */
enum class InvitationStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    REVOKED,
    EXPIRED,
    ;

    /** 현재 상태에서 [next] 상태로 전이 가능한지 질의한다. PENDING에서만 허용된다. */
    fun canTransitTo(next: InvitationStatus): Boolean {
        if (this != PENDING) return false
        return next == ACCEPTED || next == REJECTED || next == REVOKED || next == EXPIRED
    }
}

package com.sportsapp.domain.message.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * 방장 전용 행위(게스트 초대/초대 철회, 게스트 수동 방출 등)를 방장이 아닌 사용자가 시도했을 때
 * 발생한다 (TDD FR-11/15, Open Questions "게스트 초대 발신 권한: 초안은 방장만").
 *
 * `RoomInvitation`(게스트 초대 애그리거트)에 별도 방장 필드가 없어, 방의 정회원(MEMBER) 참여자를
 * 방장으로 간주하는 판정이다 — 상세 판정 로직은 호출 측
 * [com.sportsapp.domain.message.service.GuestInvitationDomainService],
 * [com.sportsapp.domain.message.service.GuestEvictionDomainService] 참고.
 */
class NotRoomHostException(
    userId: Long,
    roomId: Long,
) : BusinessException(
    errorCode = "NOT_ROOM_HOST",
    message = "User $userId is not the host of room $roomId",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}

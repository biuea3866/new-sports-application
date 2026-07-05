package com.sportsapp.domain.message.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * 방장 권한이 필요한 동작(게스트 수동 방출 등, FR-15)을 방장이 아닌 참여자가 시도할 때 발생한다.
 * `RoomInvitation`(게스트 초대 애그리거트)이 아직 이 wave에 없어, 방의 정회원(MEMBER) 참여자를
 * 방장으로 간주하는 잠정 판정이다 — 상세는 [com.sportsapp.domain.message.service.GuestEvictionDomainService].
 */
class NotRoomHostException(
    userId: Long,
    roomId: Long,
) : BusinessException(
    errorCode = "NOT_ROOM_HOST",
    message = "User $userId is not a host of room $roomId",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}

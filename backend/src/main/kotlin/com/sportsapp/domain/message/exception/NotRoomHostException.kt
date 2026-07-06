package com.sportsapp.domain.message.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * 방장 전용 행위(게스트 초대/초대 철회, 게스트 수동 방출 등)를 방장이 아닌 사용자가 시도했을 때
 * 발생한다 (TDD FR-11/15, Open Questions "게스트 초대 발신 권한: 초안은 방장만").
 *
 * 방장 판정은 `rooms.host_user_id` 단일 소스를 [com.sportsapp.domain.message.entity.Room.requireHostedBy]로
 * 위임한다 — [com.sportsapp.domain.message.service.GuestInvitationDomainService],
 * [com.sportsapp.domain.message.service.GuestEvictionDomainService] 가 이 판정을 공유해 던진다.
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

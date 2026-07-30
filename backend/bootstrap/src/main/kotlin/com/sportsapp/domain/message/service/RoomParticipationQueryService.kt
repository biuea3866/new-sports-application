package com.sportsapp.domain.message.service

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.message.exception.NotRoomParticipantException
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import com.sportsapp.domain.message.repository.RoomRepository
import com.sportsapp.domain.message.vo.RoomParticipationView
import org.springframework.stereotype.Service

/**
 * "내 방 참여정보" 조회 전용 읽기 서비스 (BE-14, FE-10 canSpeak/expiresAt 하드코딩 degrade 해소).
 *
 * [RoomContextQueryService]와 동일하게 읽기 전용 조회만 담당한다 — [MessageDomainService]에
 * 메서드를 추가하는 대신 분리했다(이미 11개 함수 보유, detekt TooManyFunctions 임계값).
 * isHost 는 `rooms.host_user_id`(BE-13) 단일 소스를 [com.sportsapp.domain.message.entity.Room.isHostedBy]로
 * 위임한다 — 호출부가 hostUserId 를 직접 비교하지 않는다.
 */
@Service
class RoomParticipationQueryService(
    private val roomRepository: RoomRepository,
    private val roomParticipantRepository: RoomParticipantRepository,
) {
    fun getMyParticipation(roomId: Long, userId: Long): RoomParticipationView {
        val room = roomRepository.findById(roomId) ?: throw ResourceNotFoundException("Room", roomId)
        val participant = roomParticipantRepository.findActiveByRoomIdAndUserId(roomId, userId)
            ?: throw NotRoomParticipantException(userId, roomId)
        return RoomParticipationView(
            roomId = room.id,
            participantType = participant.participantType,
            canSpeak = participant.canSpeak,
            expiresAt = participant.expiresAt,
            isHost = room.isHostedBy(userId),
        )
    }
}

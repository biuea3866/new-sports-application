package com.sportsapp.domain.message.service

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.exception.NotRoomParticipantException
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import com.sportsapp.domain.message.repository.RoomRepository
import java.time.ZonedDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 게스트 참여자의 만료 배치 방출·수동 방출을 담당한다 (TDD FR-14/15, `RoomParticipant.evict()` 재사용).
 *
 * 수동 방출의 방장 검증은 `rooms.host_user_id`(BE-13) 단일 소스를
 * [com.sportsapp.domain.message.entity.Room.requireHostedBy]로 위임한다 —
 * [GuestInvitationDomainService]와 동일 판정을 공유해, 과거 "활성 정회원(MEMBER) 전원을
 * 방장으로 간주"하던 판정을 제거한다. 참여자 여부와 무관하게 방장 판정만으로 거부되므로,
 * 요청자가 방 참여자가 아니어도 (호스트가 아니라면) `NotRoomHostException`이 발생한다.
 */
@Service
class GuestEvictionDomainService(
    private val roomRepository: RoomRepository,
    private val roomParticipantRepository: RoomParticipantRepository,
) {
    private val log = LoggerFactory.getLogger(GuestEvictionDomainService::class.java)

    /** 만료된 게스트를 배치로 방출한다. 참여자별 독립 처리 — 실패분은 로깅 후 계속한다. */
    fun evictExpired(): Int {
        val expiredGuests = roomParticipantRepository.findExpiredGuestsBefore(ZonedDateTime.now())
        return expiredGuests.count { evictSilently(it) }
    }

    /** 방장의 검증을 거쳐 게스트를 즉시 방출한다 (FR-15). */
    fun evict(roomId: Long, userId: Long, requesterId: Long): RoomParticipant {
        val room = roomRepository.findById(roomId) ?: throw ResourceNotFoundException("Room", roomId)
        room.requireHostedBy(requesterId)
        val target = roomParticipantRepository.findActiveByRoomIdAndUserId(roomId, userId)
            ?: throw NotRoomParticipantException(userId, roomId)
        target.evict()
        target.softDelete(requesterId)
        return roomParticipantRepository.save(target)
    }

    private fun evictSilently(participant: RoomParticipant): Boolean = try {
        participant.evict()
        participant.softDelete(null)
        roomParticipantRepository.save(participant)
        true
    } catch (exception: Exception) {
        log.error("GuestEvictionDomainService: failed to evict participantId={}", participant.id, exception)
        false
    }
}

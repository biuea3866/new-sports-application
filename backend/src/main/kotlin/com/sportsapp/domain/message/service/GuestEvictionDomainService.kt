package com.sportsapp.domain.message.service

import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.exception.NotRoomHostException
import com.sportsapp.domain.message.exception.NotRoomParticipantException
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import com.sportsapp.domain.message.vo.ParticipantType
import java.time.ZonedDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 게스트 참여자의 만료 배치 방출·수동 방출을 담당한다 (TDD FR-14/15, `RoomParticipant.evict()` 재사용).
 *
 * `RoomInvitation`(초대 애그리즘, 방장=inviterUserId)이 아직 이 wave에 없어, 수동 방출의 방장 검증은
 * 방의 정회원(MEMBER) 참여자 여부로 판정한다 — RoomInvitation 이 도입되면 실제 초대자 검증으로 교체 필요.
 */
@Service
class GuestEvictionDomainService(
    private val roomParticipantRepository: RoomParticipantRepository,
) {
    private val log = LoggerFactory.getLogger(GuestEvictionDomainService::class.java)

    /** 만료된 게스트를 배치로 방출한다. 참여자별 독립 처리 — 실패분은 로깅 후 계속한다. */
    fun evictExpired(): Int {
        val expiredGuests = roomParticipantRepository.findExpiredGuestsBefore(ZonedDateTime.now())
        return expiredGuests.count { evictSilently(it) }
    }

    /** 방장(정회원)의 검증을 거쳐 게스트를 즉시 방출한다 (FR-15). */
    fun evict(roomId: Long, userId: Long, requesterId: Long): RoomParticipant {
        requireHost(roomId, requesterId)
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

    private fun requireHost(roomId: Long, requesterId: Long) {
        val requester = roomParticipantRepository.findActiveByRoomIdAndUserId(roomId, requesterId)
            ?: throw NotRoomParticipantException(requesterId, roomId)
        if (requester.participantType != ParticipantType.MEMBER) {
            throw NotRoomHostException(requesterId, roomId)
        }
    }
}

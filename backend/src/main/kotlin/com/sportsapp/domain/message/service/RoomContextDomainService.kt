package com.sportsapp.domain.message.service

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import com.sportsapp.domain.message.repository.RoomRepository
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.message.vo.RoomType
import org.springframework.stereotype.Service

/**
 * 컨텍스트 방(예: COMMUNITY) provision·자동 가입·자동 퇴장 오케스트레이션 (BE-09, TDD FR-4/5/16/17).
 *
 * community 도메인은 이 클래스를 직접 호출하지 않는다 — `CommunityChatIntegrationEventWorker`가
 * `CommunityCreated/MemberJoined/MemberLeft` 이벤트를 소비해 UseCase를 경유해서만 호출한다
 * (도메인 패키지 교차 참조 금지, 결합은 이벤트 + ID로 최소화).
 *
 * [RoomContextQueryService](읽기 전용 조회)와는 다른 클래스다 — 이 클래스는 쓰기 오케스트레이션을 담당한다.
 */
@Service
class RoomContextDomainService(
    private val roomRepository: RoomRepository,
    private val roomParticipantRepository: RoomParticipantRepository,
) {

    /**
     * 컨텍스트 방을 provision 한다 — 이미 있으면 새로 만들지 않고 그대로 반환한다(멱등, FR-16).
     * 신규 생성 시 방장을 첫 참여자로 등록한다.
     */
    fun provision(contextType: RoomContextType, contextId: Long, name: String?, hostUserId: Long): Room {
        val existingRoom = roomRepository.findByContext(contextType, contextId)
        if (existingRoom != null) return existingRoom
        val room = roomRepository.save(Room.createForContext(RoomType.GROUP, contextType, contextId, name))
        roomParticipantRepository.save(RoomParticipant.create(room, hostUserId))
        return room
    }

    /**
     * 컨텍스트 방에 사용자를 참여시킨다 — 방이 아직 provision 되지 않았거나 이미 참여 중이면 아무 일도 하지 않는다(멱등, FR-17).
     */
    fun joinContext(contextType: RoomContextType, contextId: Long, userId: Long) {
        val room = roomRepository.findByContext(contextType, contextId) ?: return
        if (roomParticipantRepository.existsByRoomIdAndUserId(room.id, userId)) return
        roomParticipantRepository.save(RoomParticipant.create(room, userId))
    }

    /**
     * 컨텍스트 방에서 사용자를 퇴장시킨다 — 연결된 방이 없거나 활성 참여 기록이 없으면 아무 일도 하지 않는다(FR-5/17).
     */
    fun leaveContext(contextType: RoomContextType, contextId: Long, userId: Long) {
        val room = roomRepository.findByContext(contextType, contextId) ?: return
        val participant = roomParticipantRepository.findActiveByRoomIdAndUserId(room.id, userId) ?: return
        participant.softDelete(userId)
        roomParticipantRepository.save(participant)
    }
}

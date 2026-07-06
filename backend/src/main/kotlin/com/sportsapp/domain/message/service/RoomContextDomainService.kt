package com.sportsapp.domain.message.service

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import com.sportsapp.domain.message.repository.RoomRepository
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.message.vo.RoomType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 컨텍스트 방(예: COMMUNITY) provision·자동 가입·자동 퇴장 오케스트레이션 (BE-09, TDD FR-4/5/16/17).
 *
 * community 도메인은 이 클래스를 직접 호출하지 않는다 — `CommunityChatIntegrationEventWorker`가
 * `CommunityCreated/MemberJoined/MemberLeft` 이벤트를 소비해 UseCase를 경유해서만 호출한다
 * (도메인 패키지 교차 참조 금지, 결합은 이벤트 + ID로 최소화).
 *
 * [RoomContextQueryService](읽기 전용 조회)와는 다른 클래스다 — 이 클래스는 쓰기 오케스트레이션을 담당한다.
 *
 * **순서 경합(known eventual-consistency gap, PR #270 리뷰 p3)**: provision·join·leave는 모두
 * `@Async` AFTER_COMMIT으로 독립 실행되므로, 드물게 join/leave 태스크가 provision보다 먼저 실행되면
 * `findByContext`가 아직 null이라 스킵된다(아래 WARN 로그로 관측). 근본 해소(방 없으면 provision을
 * 트리거)는 이 메서드 시그니처(contextId, userId)만으로는 hostUserId를 알 수 없어 이번 범위에서
 * 구현하지 않는다 — 후속 과제. 실제로는 호스트는 provision 시점에 이미 등록되고, 일반 멤버 가입은
 * `CommunityDomainService.join`이 별도 트랜잭션이라 통상 provision 커밋 이후 도착해 발생 확률은 낮다.
 */
@Service
class RoomContextDomainService(
    private val roomRepository: RoomRepository,
    private val roomParticipantRepository: RoomParticipantRepository,
) {
    private val log = LoggerFactory.getLogger(RoomContextDomainService::class.java)

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
     * 컨텍스트 방에 사용자를 참여시킨다 — 이미 참여 중이면 아무 일도 하지 않는다(멱등, FR-17).
     * 방이 아직 provision 되지 않았으면(순서 경합) WARN 로깅 후 스킵한다 — 관측 가능하되 크래시하지 않는다.
     */
    fun joinContext(contextType: RoomContextType, contextId: Long, userId: Long) {
        val room = roomRepository.findByContext(contextType, contextId)
        if (room == null) {
            log.warn(
                "컨텍스트 방 자동 가입 스킵 — 방 미존재(provision 순서 경합 가능) contextType={} contextId={} userId={}",
                contextType,
                contextId,
                userId,
            )
            return
        }
        if (roomParticipantRepository.existsByRoomIdAndUserId(room.id, userId)) return
        roomParticipantRepository.save(RoomParticipant.create(room, userId))
    }

    /**
     * 컨텍스트 방에서 사용자를 퇴장시킨다 — 활성 참여 기록이 없으면 아무 일도 하지 않는다(FR-5/17).
     * 방이 아직 provision 되지 않았으면(순서 경합) WARN 로깅 후 스킵한다.
     */
    fun leaveContext(contextType: RoomContextType, contextId: Long, userId: Long) {
        val room = roomRepository.findByContext(contextType, contextId)
        if (room == null) {
            log.warn(
                "컨텍스트 방 자동 퇴장 스킵 — 방 미존재(provision 순서 경합 가능) contextType={} contextId={} userId={}",
                contextType,
                contextId,
                userId,
            )
            return
        }
        val participant = roomParticipantRepository.findActiveByRoomIdAndUserId(room.id, userId) ?: return
        participant.softDelete(userId)
        roomParticipantRepository.save(participant)
    }
}

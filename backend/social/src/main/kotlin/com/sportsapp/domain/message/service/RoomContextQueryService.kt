package com.sportsapp.domain.message.service

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.repository.RoomRepository
import com.sportsapp.domain.message.vo.RoomContextType
import org.springframework.stereotype.Service

/**
 * 컨텍스트 방(예: COMMUNITY) 조회 전용 읽기 서비스.
 *
 * [MessageDomainService]에 메서드를 추가하는 대신 분리했다 — 이미 11개 함수를 보유해
 * TooManyFunctions(detekt) 임계값에 걸리고, 이 조회는 다른 도메인(community)의 응답 조립을
 * 위한 순수 읽기라 책임도 명확히 구분된다. BE-09가 신설할 `RoomContextDomainService`
 * (provision/joinContext/leaveContext, 쓰기 오케스트레이션)와는 다른 클래스다.
 */
@Service
class RoomContextQueryService(
    private val roomRepository: RoomRepository,
) {
    /**
     * 컨텍스트(예: COMMUNITY)에 연결된 방을 조회한다 (BE-08 `CommunityResponse.roomId` 조회용).
     * 연결된 방이 없으면 null — 컨텍스트 방 provisioning(BE-09)이 아직 실행되지 않은 상태다.
     */
    fun findRoomByContext(contextType: RoomContextType, contextId: Long): Room? =
        roomRepository.findByContext(contextType, contextId)
}

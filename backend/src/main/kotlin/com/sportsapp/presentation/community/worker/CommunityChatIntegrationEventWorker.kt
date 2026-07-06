package com.sportsapp.presentation.community.worker

import com.sportsapp.application.message.dto.JoinContextRoomCommand
import com.sportsapp.application.message.dto.LeaveContextRoomCommand
import com.sportsapp.application.message.dto.ProvisionContextRoomCommand
import com.sportsapp.application.message.usecase.JoinContextRoomUseCase
import com.sportsapp.application.message.usecase.LeaveContextRoomUseCase
import com.sportsapp.application.message.usecase.ProvisionContextRoomUseCase
import com.sportsapp.domain.community.event.CommunityCreatedEvent
import com.sportsapp.domain.community.event.CommunityMemberJoinedEvent
import com.sportsapp.domain.community.event.CommunityMemberLeftEvent
import com.sportsapp.domain.message.vo.RoomContextType
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * community 도메인 이벤트(Layer 1 — 같은 앱·연관 컨텍스트)를 소비해 전용 그룹 채팅방을
 * 자동 provision·자동 가입·자동 퇴장시킨다 (BE-09, TDD FR-4/5/16/17).
 *
 * community가 message UseCase를 직접 호출하지 않고 이벤트 + ID로만 연결한다(도메인 패키지
 * 교차 참조 금지). 각 핸들러는 원 트랜잭션 커밋 이후(AFTER_COMMIT)에만 실행되어, 커밋되지
 * 않은 커뮤니티/멤버십을 참조하는 사고를 막는다. 개별 처리 실패는 로깅만 하고 다른 리스너의
 * 실행을 막지 않는다.
 *
 * `@Async` 필수 — 각 UseCase가 새 `@Transactional` DB 쓰기(Room/RoomParticipant 생성)를 수행하는데,
 * 같은 스레드에서 원 트랜잭션의 AFTER_COMMIT 콜백 안에 이어 붙이면 Spring이 아직 언바인딩되지 않은
 * (이미 물리적으로 커밋된) 리소스 홀더에 새 트랜잭션을 편승시켜 쓰기가 유실될 수 있다
 * (`ProcessAlertUseCase`/`AlertProcessingEventWorker`와 동일한 이유로 `@Async` 채택).
 *
 * CommunityCreatedEvent.name(BE-14)을 그대로 실어 컨텍스트 방을 이름과 함께 provision 한다.
 */
@Component
class CommunityChatIntegrationEventWorker(
    private val provisionContextRoomUseCase: ProvisionContextRoomUseCase,
    private val joinContextRoomUseCase: JoinContextRoomUseCase,
    private val leaveContextRoomUseCase: LeaveContextRoomUseCase,
) {
    private val log = LoggerFactory.getLogger(CommunityChatIntegrationEventWorker::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleCommunityCreated(event: CommunityCreatedEvent) {
        try {
            provisionContextRoomUseCase.execute(
                ProvisionContextRoomCommand(
                    contextType = RoomContextType.COMMUNITY,
                    contextId = event.aggregateId,
                    name = event.name,
                    hostUserId = event.hostUserId,
                ),
            )
        } catch (exception: Exception) {
            log.error("컨텍스트 방 provision 실패 — communityId={}", event.aggregateId, exception)
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleMemberJoined(event: CommunityMemberJoinedEvent) {
        try {
            joinContextRoomUseCase.execute(
                JoinContextRoomCommand(
                    contextType = RoomContextType.COMMUNITY,
                    contextId = event.communityId,
                    userId = event.userId,
                ),
            )
        } catch (exception: Exception) {
            log.error(
                "컨텍스트 방 자동 가입 실패 — communityId={} userId={}",
                event.communityId,
                event.userId,
                exception,
            )
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleMemberLeft(event: CommunityMemberLeftEvent) {
        try {
            leaveContextRoomUseCase.execute(
                LeaveContextRoomCommand(
                    contextType = RoomContextType.COMMUNITY,
                    contextId = event.communityId,
                    userId = event.userId,
                ),
            )
        } catch (exception: Exception) {
            log.error(
                "컨텍스트 방 자동 퇴장 실패 — communityId={} userId={}",
                event.communityId,
                event.userId,
                exception,
            )
        }
    }
}

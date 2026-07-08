package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.BroadcastReadCommand
import com.sportsapp.domain.message.service.ReadCursorDomainService
import org.springframework.stereotype.Service

/**
 * 커밋된 읽음 커서를 실시간 구독자에게 팬아웃한다 (BE-05).
 * DB 트랜잭션이 필요 없는 순수 팬아웃이라 `@Transactional` 을 두지 않는다.
 */
@Service
class BroadcastReadUseCase(
    private val readCursorDomainService: ReadCursorDomainService,
) {
    fun execute(command: BroadcastReadCommand) {
        readCursorDomainService.broadcastRead(
            roomId = command.roomId,
            userId = command.userId,
            lastReadMessageId = command.lastReadMessageId,
        )
    }
}

package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.BroadcastMessageCommand
import com.sportsapp.domain.message.service.MessageDomainService
import org.springframework.stereotype.Service

/**
 * 커밋된 메시지를 실시간 구독자에게 팬아웃한다 (BE-04 정정).
 * DB 트랜잭션이 필요 없는 순수 팬아웃이라 `@Transactional` 을 두지 않는다.
 */
@Service
class BroadcastMessageUseCase(
    private val messageDomainService: MessageDomainService,
) {
    fun execute(command: BroadcastMessageCommand) {
        messageDomainService.broadcastMessage(
            roomId = command.roomId,
            messageId = command.messageId,
            senderId = command.senderId,
            content = command.content,
            sentAt = command.sentAt,
        )
    }
}

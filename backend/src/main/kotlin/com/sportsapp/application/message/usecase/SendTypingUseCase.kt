package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.BroadcastTypingCommand
import com.sportsapp.domain.message.service.MessageDomainService
import org.springframework.stereotype.Service

/**
 * 타이핑 신호를 실시간 구독자에게 팬아웃한다 (BE-04 정정).
 * 영속화가 필요 없는 신호라 `@Transactional` 을 두지 않는다.
 */
@Service
class SendTypingUseCase(
    private val messageDomainService: MessageDomainService,
) {
    fun execute(command: BroadcastTypingCommand) {
        messageDomainService.broadcastTyping(
            roomId = command.roomId,
            userId = command.userId,
            typing = command.typing,
        )
    }
}

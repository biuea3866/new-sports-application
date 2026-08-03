package com.sportsapp.application.operator.usecase

import com.sportsapp.application.operator.dto.RecordOperatorInboxEventCommand
import com.sportsapp.domain.operator.service.OperatorInboxNotificationDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 도메인 이벤트를 파트너 운영 인박스에 적재한다.
 *
 * 중복 수신은 정상 시나리오이므로 도메인 서비스의 멱등 적재(createOrSkip)에 위임한다 —
 * 이미 받은 이벤트면 조용히 건너뛴다.
 */
@Service
class RecordOperatorInboxEventUseCase(
    private val operatorInboxNotificationDomainService: OperatorInboxNotificationDomainService,
) {
    @Transactional
    fun execute(command: RecordOperatorInboxEventCommand) {
        operatorInboxNotificationDomainService.createOrSkip(
            eventId = command.eventId,
            recipientUserId = command.recipientUserId,
            type = command.type,
            title = command.title,
            body = command.body,
            link = command.link,
        )
    }
}

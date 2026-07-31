package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.EvictGuestCommand
import com.sportsapp.domain.message.service.GuestEvictionDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 방장의 게스트 수동 방출 요청을 처리한다 (FR-15). */
@Service
class EvictGuestUseCase(
    private val guestEvictionDomainService: GuestEvictionDomainService,
) {
    @Transactional
    fun execute(command: EvictGuestCommand) {
        guestEvictionDomainService.evict(
            roomId = command.roomId,
            userId = command.userId,
            requesterId = command.requesterId,
        )
    }
}

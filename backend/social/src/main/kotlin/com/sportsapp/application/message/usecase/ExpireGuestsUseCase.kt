package com.sportsapp.application.message.usecase

import com.sportsapp.domain.message.service.GuestEvictionDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 만료된 게스트를 배치로 방출한다 (FR-14, `GuestExpiryScheduler` 전용). */
@Service
class ExpireGuestsUseCase(
    private val guestEvictionDomainService: GuestEvictionDomainService,
) {
    @Transactional
    fun execute(): Int = guestEvictionDomainService.evictExpired()
}

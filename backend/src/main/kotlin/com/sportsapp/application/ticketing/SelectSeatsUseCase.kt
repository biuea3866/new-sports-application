package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.TicketingDomainService
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

private const val SEAT_LOCK_TTL_SECONDS = 300L

@Service
class SelectSeatsUseCase(
    private val ticketingDomainService: TicketingDomainService,
) {
    fun execute(command: SelectSeatsCommand): SelectSeatsResponse {
        val lockId = ticketingDomainService.tryLockSeats(command.eventId, command.seatIds, command.userId)
        return SelectSeatsResponse(
            lockId = lockId,
            expiresAt = ZonedDateTime.now().plusSeconds(SEAT_LOCK_TTL_SECONDS),
        )
    }
}

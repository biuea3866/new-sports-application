package com.sportsapp.application.booking

import com.sportsapp.domain.booking.BookingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListNoShowBookingsUseCase(
    private val bookingDomainService: BookingDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: ListNoShowBookingsCommand): ListNoShowBookingsResponse {
        requirePeriodNotInFuture(command)
        val page = bookingDomainService.listNoShows(
            operatorUserId = command.operatorUserId,
            facilityId = command.facilityId,
            from = command.from,
            to = command.to,
            pageable = command.pageable,
        )
        return ListNoShowBookingsResponse.of(page.map { NoShowBookingResponse.of(it) })
    }

    private fun requirePeriodNotInFuture(command: ListNoShowBookingsCommand) {
        val now = java.time.ZonedDateTime.now()
        require(command.from.isBefore(now)) { "from must be in the past" }
    }
}

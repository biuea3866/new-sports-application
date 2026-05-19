package com.sportsapp.infrastructure.persistence.ticketing

import com.sportsapp.domain.ticketing.EventRepository
import com.sportsapp.domain.ticketing.SeatRepository
import com.sportsapp.domain.ticketing.TicketingDomainService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TicketingDomainServiceConfig {

    @Bean
    fun ticketingDomainService(
        eventRepository: EventRepository,
        seatRepository: SeatRepository,
    ): TicketingDomainService = TicketingDomainService(eventRepository, seatRepository)
}

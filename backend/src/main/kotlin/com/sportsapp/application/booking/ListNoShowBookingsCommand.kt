package com.sportsapp.application.booking

import org.springframework.data.domain.Pageable
import java.time.ZonedDateTime

data class ListNoShowBookingsCommand(
    val operatorUserId: Long,
    val facilityId: String?,
    val from: ZonedDateTime,
    val to: ZonedDateTime,
    val pageable: Pageable,
)

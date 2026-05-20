package com.sportsapp.presentation.ticketing

import com.sportsapp.application.ticketing.EventDetailResponse
import com.sportsapp.application.ticketing.EventResponse
import com.sportsapp.application.ticketing.GetEventUseCase
import com.sportsapp.application.ticketing.ListEventsUseCase
import com.sportsapp.domain.ticketing.EventCriteria
import com.sportsapp.domain.ticketing.EventStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.ZonedDateTime

@RestController
@RequestMapping("/events")
class EventApiController(
    private val listEventsUseCase: ListEventsUseCase,
    private val getEventUseCase: GetEventUseCase,
) {

    @GetMapping
    fun listEvents(
        @RequestParam(required = false) status: EventStatus?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        startsAtFrom: ZonedDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        startsAtTo: ZonedDateTime?,
        @PageableDefault(size = 20, sort = ["startsAt"]) pageable: Pageable,
    ): Page<EventResponse> {
        val criteria = EventCriteria(
            status = status,
            startsAtFrom = startsAtFrom,
            startsAtTo = startsAtTo,
        )
        return listEventsUseCase.execute(criteria, pageable)
    }

    @GetMapping("/{id}")
    fun getEvent(@PathVariable id: Long): EventDetailResponse =
        getEventUseCase.execute(id)
}

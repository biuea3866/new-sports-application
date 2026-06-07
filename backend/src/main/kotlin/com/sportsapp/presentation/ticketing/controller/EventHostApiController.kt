package com.sportsapp.presentation.ticketing.controller

import com.sportsapp.application.ticketing.usecase.CloseMyEventUseCase
import com.sportsapp.application.ticketing.usecase.CreateMyEventUseCase
import com.sportsapp.application.ticketing.dto.CreateMyEventResult
import com.sportsapp.application.ticketing.usecase.DeleteMyEventUseCase
import com.sportsapp.application.ticketing.dto.EventResponse
import com.sportsapp.application.ticketing.usecase.GetMyEventWithSalesUseCase
import com.sportsapp.application.ticketing.usecase.ListMyEventsUseCase
import com.sportsapp.application.ticketing.dto.MyEventWithSalesResponse
import com.sportsapp.application.ticketing.usecase.OpenMyEventUseCase
import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.presentation.ticketing.dto.request.CreateMyEventRequest
import com.sportsapp.domain.ticketing.entity.EventStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriComponentsBuilder

@RestController
@RequestMapping("/api/event-host/events")
class EventHostApiController(
    private val createMyEventUseCase: CreateMyEventUseCase,
    private val openMyEventUseCase: OpenMyEventUseCase,
    private val closeMyEventUseCase: CloseMyEventUseCase,
    private val deleteMyEventUseCase: DeleteMyEventUseCase,
    private val listMyEventsUseCase: ListMyEventsUseCase,
    private val getMyEventWithSalesUseCase: GetMyEventWithSalesUseCase,
    private val ownershipGuard: OwnershipGuard,
) {

    @PostMapping
    @PreAuthorize("hasRole('EVENT_HOST')")
    fun createEvent(
        @RequestBody request: CreateMyEventRequest,
        uriBuilder: UriComponentsBuilder,
    ): ResponseEntity<CreateMyEventResult> {
        val authUserId = ownershipGuard.authUserId()
        val result = createMyEventUseCase.execute(request.toCommand(authUserId))
        val location = uriBuilder.path("/api/event-host/events/{id}").buildAndExpand(result.eventId).toUri()
        return ResponseEntity.created(location).body(result)
    }

    @GetMapping
    @PreAuthorize("hasRole('EVENT_HOST')")
    fun listMyEvents(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) status: EventStatus?,
    ): ResponseEntity<Page<EventResponse>> {
        val authUserId = ownershipGuard.authUserId()
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startsAt"))
        return ResponseEntity.ok(listMyEventsUseCase.execute(authUserId, pageable, status))
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('EVENT_HOST')")
    fun getMyEvent(@PathVariable id: Long): ResponseEntity<MyEventWithSalesResponse> {
        val authUserId = ownershipGuard.authUserId()
        return ResponseEntity.ok(getMyEventWithSalesUseCase.execute(id, authUserId))
    }

    @PostMapping("/{id}/open")
    @PreAuthorize("hasRole('EVENT_HOST')")
    fun openEvent(@PathVariable id: Long): ResponseEntity<Void> {
        val authUserId = ownershipGuard.authUserId()
        openMyEventUseCase.execute(id, authUserId)
        return ResponseEntity.ok().build()
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('EVENT_HOST')")
    fun closeEvent(@PathVariable id: Long): ResponseEntity<Void> {
        val authUserId = ownershipGuard.authUserId()
        closeMyEventUseCase.execute(id, authUserId)
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('EVENT_HOST')")
    fun deleteEvent(@PathVariable id: Long): ResponseEntity<Void> {
        val authUserId = ownershipGuard.authUserId()
        deleteMyEventUseCase.execute(id, authUserId)
        return ResponseEntity.noContent().build()
    }
}

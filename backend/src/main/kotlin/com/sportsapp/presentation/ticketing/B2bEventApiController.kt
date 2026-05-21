package com.sportsapp.presentation.ticketing

import com.sportsapp.application.ticketing.CloseMyEventUseCase
import com.sportsapp.application.ticketing.CreateMyEventUseCase
import com.sportsapp.application.ticketing.GetMyEventUseCase
import com.sportsapp.application.ticketing.ListMyEventsUseCase
import com.sportsapp.application.ticketing.MyEventResponse
import com.sportsapp.application.ticketing.UpdateMyEventUseCase
import com.sportsapp.domain.common.security.OwnershipGuard
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/b2b/my/events")
@PreAuthorize("hasRole('EVENT_HOST')")
class B2bEventApiController(
    private val createMyEventUseCase: CreateMyEventUseCase,
    private val listMyEventsUseCase: ListMyEventsUseCase,
    private val getMyEventUseCase: GetMyEventUseCase,
    private val updateMyEventUseCase: UpdateMyEventUseCase,
    private val closeMyEventUseCase: CloseMyEventUseCase,
    private val ownershipGuard: OwnershipGuard,
) {

    @PostMapping
    fun createMyEvent(
        @RequestBody request: CreateMyEventRequest,
    ): ResponseEntity<MyEventResponse> {
        val ownerUserId = ownershipGuard.authUserId()
        val response = createMyEventUseCase.execute(request.toCommand(ownerUserId))
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping
    fun listMyEvents(
        @PageableDefault(size = 20, sort = ["startsAt"]) pageable: Pageable,
    ): Page<MyEventResponse> {
        val ownerUserId = ownershipGuard.authUserId()
        return listMyEventsUseCase.execute(ownerUserId, pageable)
    }

    @GetMapping("/{id}")
    fun getMyEvent(
        @PathVariable id: Long,
    ): MyEventResponse {
        val ownerUserId = ownershipGuard.authUserId()
        return getMyEventUseCase.execute(id, ownerUserId)
    }

    @PutMapping("/{id}")
    fun updateMyEvent(
        @PathVariable id: Long,
        @RequestBody request: UpdateMyEventRequest,
    ): MyEventResponse {
        val ownerUserId = ownershipGuard.authUserId()
        return updateMyEventUseCase.execute(request.toCommand(id, ownerUserId))
    }

    @PostMapping("/{id}/close")
    fun closeMyEvent(
        @PathVariable id: Long,
    ): MyEventResponse {
        val ownerUserId = ownershipGuard.authUserId()
        return closeMyEventUseCase.execute(id, ownerUserId)
    }
}

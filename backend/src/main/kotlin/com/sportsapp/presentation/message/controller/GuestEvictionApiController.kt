package com.sportsapp.presentation.message.controller

import com.sportsapp.application.message.dto.EvictGuestCommand
import com.sportsapp.application.message.usecase.EvictGuestUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 방장의 게스트 수동 방출 엔드포인트 (TDD FR-15, `EvictGuestUseCase`). */
@RestController
@RequestMapping("/rooms/{roomId}/guests/{userId}")
class GuestEvictionApiController(
    private val evictGuestUseCase: EvictGuestUseCase,
) {

    @PostMapping("/evict")
    fun evictGuest(
        @RequestHeader("X-User-Id") requesterId: Long, // TODO(AUTH-03): SecurityContext로 교체
        @PathVariable roomId: Long,
        @PathVariable userId: Long,
    ): ResponseEntity<Void> {
        evictGuestUseCase.execute(
            EvictGuestCommand(roomId = roomId, userId = userId, requesterId = requesterId),
        )
        return ResponseEntity.noContent().build()
    }
}

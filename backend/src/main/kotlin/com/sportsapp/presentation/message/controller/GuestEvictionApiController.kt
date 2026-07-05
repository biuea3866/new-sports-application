package com.sportsapp.presentation.message.controller

import com.sportsapp.application.message.dto.EvictGuestCommand
import com.sportsapp.application.message.usecase.EvictGuestUseCase
import com.sportsapp.domain.user.vo.UserPrincipal
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 방장의 게스트 수동 방출 엔드포인트 (TDD FR-15, `EvictGuestUseCase`).
 *
 * `/rooms` 하위 경로는 `RoomApiController`/`MessageApiController`와 동일하게 Authorization: Bearer JWT
 * (`@AuthenticationPrincipal UserPrincipal`) 로 신원을 확인한다 — X-User-Id 헤더는 클라이언트가
 * 임의로 조작할 수 있어 방장 검증(`GuestEvictionDomainService.requireHost`)이 무력화되므로 사용하지 않는다.
 */
@RestController
@RequestMapping("/rooms/{roomId}/guests/{userId}")
class GuestEvictionApiController(
    private val evictGuestUseCase: EvictGuestUseCase,
) {

    @PostMapping("/evict")
    fun evictGuest(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable roomId: Long,
        @PathVariable userId: Long,
    ): ResponseEntity<Void> {
        evictGuestUseCase.execute(
            EvictGuestCommand(roomId = roomId, userId = userId, requesterId = principal.id),
        )
        return ResponseEntity.noContent().build()
    }
}

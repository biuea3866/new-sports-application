package com.sportsapp.presentation.message.controller

import com.sportsapp.application.message.dto.InvitationResponse
import com.sportsapp.application.message.usecase.AcceptInvitationUseCase
import com.sportsapp.application.message.usecase.InviteGuestUseCase
import com.sportsapp.application.message.usecase.ListMyInvitationsUseCase
import com.sportsapp.application.message.usecase.RejectInvitationUseCase
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.message.dto.request.InviteGuestRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 게스트 초대(초대·수락·거절·수신함) — TDD FR-11/12/13/14.
 *
 * BE-12에서 SecurityConfig의 `/rooms` 하위 경로 전체가 `authenticated()`로 승격되어(#257),
 * RoomApiController/MessageApiController와 동일하게 `@AuthenticationPrincipal principal: UserPrincipal`
 * (non-null) + `principal.id`로 요청자를 식별한다. 미인증 요청은 Spring Security가 컨트롤러
 * 진입 전에 401로 차단한다.
 */
@RestController
@RequestMapping("/rooms")
class InvitationApiController(
    private val inviteGuestUseCase: InviteGuestUseCase,
    private val acceptInvitationUseCase: AcceptInvitationUseCase,
    private val rejectInvitationUseCase: RejectInvitationUseCase,
    private val listMyInvitationsUseCase: ListMyInvitationsUseCase,
) {

    @PostMapping("/{roomId}/invitations")
    fun invite(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable roomId: Long,
        @RequestBody request: InviteGuestRequest,
    ): ResponseEntity<InvitationResponse> {
        val invitation = inviteGuestUseCase.execute(request.toCommand(roomId, principal.id))
        return ResponseEntity.status(HttpStatus.CREATED).body(InvitationResponse.of(invitation))
    }

    @GetMapping("/invitations/me")
    fun listMyInvitations(@AuthenticationPrincipal principal: UserPrincipal): ResponseEntity<List<InvitationResponse>> {
        val invitations = listMyInvitationsUseCase.execute(principal.id).map { InvitationResponse.of(it) }
        return ResponseEntity.ok(invitations)
    }

    @PostMapping("/invitations/{id}/accept")
    fun accept(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ): ResponseEntity<InvitationResponse> {
        val invitation = acceptInvitationUseCase.execute(invitationId = id, userId = principal.id)
        return ResponseEntity.ok(InvitationResponse.of(invitation))
    }

    @PostMapping("/invitations/{id}/reject")
    fun reject(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ): ResponseEntity<InvitationResponse> {
        val invitation = rejectInvitationUseCase.execute(invitationId = id, userId = principal.id)
        return ResponseEntity.ok(InvitationResponse.of(invitation))
    }
}

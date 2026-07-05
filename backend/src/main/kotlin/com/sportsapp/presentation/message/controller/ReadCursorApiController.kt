package com.sportsapp.presentation.message.controller

import com.sportsapp.application.message.dto.RoomUnreadResponse
import com.sportsapp.application.message.usecase.GetMyUnreadUseCase
import com.sportsapp.application.message.usecase.MarkReadUseCase
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.security.CurrentUser
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/rooms")
class ReadCursorApiController(
    private val markReadUseCase: MarkReadUseCase,
    private val getMyUnreadUseCase: GetMyUnreadUseCase,
) {

    @PostMapping("/{roomId}/read")
    fun markRead(
        @CurrentUser principal: UserPrincipal,
        @PathVariable roomId: Long,
        @RequestBody request: MarkReadRequest,
    ): ResponseEntity<RoomUnreadResponse> {
        val response = markReadUseCase.execute(roomId, principal.id, request.lastReadMessageId)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/me/unread")
    fun getMyUnread(@CurrentUser principal: UserPrincipal): ResponseEntity<List<RoomUnreadResponse>> =
        ResponseEntity.ok(getMyUnreadUseCase.execute(principal.id))
}

data class MarkReadRequest(val lastReadMessageId: Long)

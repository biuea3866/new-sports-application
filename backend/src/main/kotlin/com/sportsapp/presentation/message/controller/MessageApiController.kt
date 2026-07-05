package com.sportsapp.presentation.message.controller

import com.sportsapp.application.message.usecase.ListMessagesUseCase
import com.sportsapp.application.message.usecase.SendMessageUseCase
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.message.dto.request.SendMessageRequest
import com.sportsapp.presentation.message.dto.response.ListMessagesResponse
import com.sportsapp.presentation.message.dto.response.MessageResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/rooms/{roomId}/messages")
class MessageApiController(
    private val sendMessageUseCase: SendMessageUseCase,
    private val listMessagesUseCase: ListMessagesUseCase,
) {

    @PostMapping
    fun sendMessage(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable roomId: Long,
        @Valid @RequestBody request: SendMessageRequest,
    ): ResponseEntity<MessageResponse> {
        val message = sendMessageUseCase.execute(request.toCommand(roomId, principal.id))
        return ResponseEntity.status(HttpStatus.CREATED).body(MessageResponse.of(message))
    }

    @GetMapping
    fun listMessages(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable roomId: Long,
        @RequestParam(required = false) cursor: String?,
    ): ResponseEntity<ListMessagesResponse> {
        val messages = listMessagesUseCase.execute(roomId, principal.id, cursor)
        return ResponseEntity.ok(ListMessagesResponse.of(messages, PAGE_SIZE))
    }

    companion object {
        private const val PAGE_SIZE = 30
    }
}

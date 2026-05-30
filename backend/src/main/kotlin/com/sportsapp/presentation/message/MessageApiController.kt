package com.sportsapp.presentation.message

import com.sportsapp.application.message.ListMessagesResponse
import com.sportsapp.application.message.ListMessagesUseCase
import com.sportsapp.application.message.MessageResponse
import com.sportsapp.application.message.SendMessageUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
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
        @RequestHeader("X-User-Id") userId: Long, // TODO(AUTH-03): SecurityContext로 교체
        @PathVariable roomId: Long,
        @Valid @RequestBody request: SendMessageRequest,
    ): ResponseEntity<MessageResponse> {
        val response = sendMessageUseCase.execute(request.toCommand(roomId, userId))
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping
    fun listMessages(
        @RequestHeader("X-User-Id") userId: Long, // TODO(AUTH-03): SecurityContext로 교체
        @PathVariable roomId: Long,
        @RequestParam(required = false) cursor: String?,
    ): ResponseEntity<ListMessagesResponse> {
        return ResponseEntity.ok(listMessagesUseCase.execute(roomId, userId, cursor))
    }
}

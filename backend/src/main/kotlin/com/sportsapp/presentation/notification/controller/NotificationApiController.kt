package com.sportsapp.presentation.notification.controller

import com.sportsapp.application.notification.usecase.GetUnreadCountUseCase
import com.sportsapp.application.notification.ListMyNotificationsCommand
import com.sportsapp.application.notification.usecase.ListMyNotificationsUseCase
import com.sportsapp.application.notification.MarkNotificationReadCommand
import com.sportsapp.application.notification.usecase.MarkNotificationReadUseCase
import com.sportsapp.presentation.notification.RegisterPushTokenRequest
import com.sportsapp.presentation.notification.dto.response.NotificationPageResponse
import com.sportsapp.presentation.notification.dto.response.NotificationResponse
import com.sportsapp.presentation.notification.dto.response.PushTokenResponse
import com.sportsapp.application.notification.usecase.RegisterPushTokenUseCase
import com.sportsapp.presentation.notification.dto.response.UnreadCountResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/notifications")
class NotificationApiController(
    private val listMyNotificationsUseCase: ListMyNotificationsUseCase,
    private val markNotificationReadUseCase: MarkNotificationReadUseCase,
    private val getUnreadCountUseCase: GetUnreadCountUseCase,
    private val registerPushTokenUseCase: RegisterPushTokenUseCase,
) {
    @PostMapping("/push-tokens")
    fun registerPushToken(
        @RequestHeader("X-User-Id") userId: Long,
        @RequestBody request: RegisterPushTokenRequest,
    ): ResponseEntity<PushTokenResponse> {
        val result = registerPushTokenUseCase.execute(request.toCommand(userId))
        return ResponseEntity.ok(PushTokenResponse(id = result.id, platform = result.platform))
    }
    @GetMapping("/me")
    fun listMyNotifications(
        @RequestHeader("X-User-Id") userId: Long,
        @RequestParam(defaultValue = "false") onlyUnread: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<NotificationPageResponse> {
        val command = ListMyNotificationsCommand(
            userId = userId,
            onlyUnread = onlyUnread,
            page = page,
            size = size,
        )
        val result = listMyNotificationsUseCase.execute(command)
        return ResponseEntity.ok(NotificationPageResponse.of(result))
    }

    @GetMapping("/me/unread-count")
    fun getUnreadCount(
        @RequestHeader("X-User-Id") userId: Long,
    ): ResponseEntity<UnreadCountResponse> =
        ResponseEntity.ok(UnreadCountResponse(getUnreadCountUseCase.execute(userId)))

    @PatchMapping("/{id}/read")
    fun markRead(
        @PathVariable id: Long,
        @RequestHeader("X-User-Id") userId: Long,
    ): ResponseEntity<NotificationResponse> {
        val command = MarkNotificationReadCommand(notificationId = id, userId = userId)
        return ResponseEntity.ok(NotificationResponse.of(markNotificationReadUseCase.execute(command)))
    }
}

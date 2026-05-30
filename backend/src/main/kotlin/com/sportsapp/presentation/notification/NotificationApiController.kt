package com.sportsapp.presentation.notification

import com.sportsapp.application.notification.GetUnreadCountUseCase
import com.sportsapp.application.notification.ListMyNotificationsCommand
import com.sportsapp.application.notification.ListMyNotificationsUseCase
import com.sportsapp.application.notification.MarkNotificationReadCommand
import com.sportsapp.application.notification.MarkNotificationReadUseCase
import com.sportsapp.application.notification.NotificationPageResponse
import com.sportsapp.application.notification.NotificationResponse
import com.sportsapp.application.notification.PushTokenResponse
import com.sportsapp.application.notification.RegisterPushTokenUseCase
import com.sportsapp.application.notification.UnreadCountResponse
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
    ): ResponseEntity<PushTokenResponse> =
        ResponseEntity.ok(registerPushTokenUseCase.execute(request.toCommand(userId)))
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
        return ResponseEntity.ok(listMyNotificationsUseCase.execute(command))
    }

    @GetMapping("/me/unread-count")
    fun getUnreadCount(
        @RequestHeader("X-User-Id") userId: Long,
    ): ResponseEntity<UnreadCountResponse> =
        ResponseEntity.ok(getUnreadCountUseCase.execute(userId))

    @PatchMapping("/{id}/read")
    fun markRead(
        @PathVariable id: Long,
        @RequestHeader("X-User-Id") userId: Long,
    ): ResponseEntity<NotificationResponse> {
        val command = MarkNotificationReadCommand(notificationId = id, userId = userId)
        return ResponseEntity.ok(markNotificationReadUseCase.execute(command))
    }
}

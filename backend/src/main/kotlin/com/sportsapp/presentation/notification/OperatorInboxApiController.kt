package com.sportsapp.presentation.notification

import com.sportsapp.application.operator.GetOperatorInboxUnreadCountUseCase
import com.sportsapp.application.operator.ListOperatorInboxNotificationsCommand
import com.sportsapp.application.operator.ListOperatorInboxNotificationsUseCase
import com.sportsapp.application.operator.OperatorInboxNotificationPageResponse
import com.sportsapp.application.operator.OperatorInboxNotificationResponse
import com.sportsapp.application.operator.OperatorInboxUnreadCountResponse
import com.sportsapp.application.operator.UpdateOperatorInboxNotificationStatusCommand
import com.sportsapp.application.operator.UpdateOperatorInboxNotificationStatusUseCase
import com.sportsapp.domain.operator.inbox.OperatorInboxNotificationStatus
import com.sportsapp.domain.operator.inbox.OperatorInboxNotificationType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/operator/inbox")
class OperatorInboxApiController(
    private val listOperatorInboxNotificationsUseCase: ListOperatorInboxNotificationsUseCase,
    private val updateOperatorInboxNotificationStatusUseCase: UpdateOperatorInboxNotificationStatusUseCase,
    private val getOperatorInboxUnreadCountUseCase: GetOperatorInboxUnreadCountUseCase,
) {
    @GetMapping
    fun listNotifications(
        @RequestHeader("X-User-Id") userId: Long,
        @RequestParam(required = false) type: OperatorInboxNotificationType?,
        @RequestParam(required = false) status: OperatorInboxNotificationStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<OperatorInboxNotificationPageResponse> {
        val command = ListOperatorInboxNotificationsCommand(
            recipientUserId = userId,
            typeFilter = type,
            statusFilter = status,
            page = page,
            size = size,
        )
        return ResponseEntity.ok(listOperatorInboxNotificationsUseCase.execute(command))
    }

    @GetMapping("/unread-count")
    fun getUnreadCount(
        @RequestHeader("X-User-Id") userId: Long,
    ): ResponseEntity<OperatorInboxUnreadCountResponse> =
        ResponseEntity.ok(getOperatorInboxUnreadCountUseCase.execute(userId))

    @PatchMapping("/{id}/read")
    fun markRead(
        @PathVariable id: Long,
        @RequestHeader("X-User-Id") userId: Long,
    ): ResponseEntity<OperatorInboxNotificationResponse> {
        val command = UpdateOperatorInboxNotificationStatusCommand(
            notificationId = id,
            recipientUserId = userId,
            targetStatus = OperatorInboxNotificationStatus.READ,
        )
        return ResponseEntity.ok(updateOperatorInboxNotificationStatusUseCase.execute(command))
    }
}

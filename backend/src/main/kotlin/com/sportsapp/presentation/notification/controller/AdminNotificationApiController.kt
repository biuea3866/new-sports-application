package com.sportsapp.presentation.notification.controller

import com.sportsapp.presentation.notification.SendNotificationRequest
import com.sportsapp.presentation.notification.dto.response.NotificationResponse
import com.sportsapp.application.notification.usecase.SendNotificationUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/notifications")
class AdminNotificationApiController(
    private val sendNotificationUseCase: SendNotificationUseCase,
) {
    @PostMapping("/send")
    fun send(
        @RequestBody request: SendNotificationRequest,
    ): ResponseEntity<NotificationResponse> =
        ResponseEntity.ok(NotificationResponse.of(sendNotificationUseCase.execute(request.toCommand())))
}

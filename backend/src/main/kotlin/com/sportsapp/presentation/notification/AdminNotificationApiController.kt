package com.sportsapp.presentation.notification

import com.sportsapp.application.notification.NotificationResponse
import com.sportsapp.application.notification.SendNotificationUseCase
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
        ResponseEntity.ok(sendNotificationUseCase.execute(request.toCommand()))
}

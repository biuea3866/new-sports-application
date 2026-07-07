package com.sportsapp.presentation.recruitment.controller

import com.sportsapp.application.recruitment.dto.ApplicationResponse
import com.sportsapp.application.recruitment.dto.CancelApplicationCommand
import com.sportsapp.application.recruitment.usecase.CancelApplicationUseCase
import com.sportsapp.application.recruitment.usecase.ListMyApplicationsUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 신청자 본인 관점 REST 계약 (FR-4). `/recruitments/{id}/applications`(개설자 관점 목록·신청)와
 * 달리 인증 principal(X-User-Id) 기준 본인 신청만 다룬다.
 */
@RestController
@RequestMapping("/applications")
class ApplicationApiController(
    private val listMyApplicationsUseCase: ListMyApplicationsUseCase,
    private val cancelApplicationUseCase: CancelApplicationUseCase,
) {
    @GetMapping
    fun listMine(
        @RequestHeader("X-User-Id") userId: Long,
    ): ResponseEntity<List<ApplicationResponse>> =
        ResponseEntity.ok(listMyApplicationsUseCase.execute(userId))

    @PostMapping("/{id}/cancel")
    fun cancel(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<ApplicationResponse> {
        val response = cancelApplicationUseCase.execute(
            CancelApplicationCommand(applicationId = id, applicantUserId = userId),
        )
        return ResponseEntity.ok(response)
    }
}

package com.sportsapp.presentation.mcp.controller
import com.sportsapp.presentation.mcp.dto.request.MarkAnomalyFalsePositiveRequest

import com.sportsapp.application.mcp.dto.ListMyAnomalyEventsCommand
import com.sportsapp.application.mcp.dto.ListMyAnomalyEventsResponse
import com.sportsapp.application.mcp.usecase.ListMyAnomalyEventsUseCase
import com.sportsapp.application.mcp.dto.MarkAnomalyFalsePositiveCommand
import com.sportsapp.application.mcp.usecase.MarkAnomalyFalsePositiveUseCase
import com.sportsapp.application.mcp.dto.McpAnomalyEventResponse
import com.sportsapp.domain.user.vo.UserPrincipal
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/mcp/anomaly-events")
class McpAnomalyEventApiController(
    private val listMyAnomalyEventsUseCase: ListMyAnomalyEventsUseCase,
    private val markAnomalyFalsePositiveUseCase: MarkAnomalyFalsePositiveUseCase,
) {
    @GetMapping
    fun listMyAnomalyEvents(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Min(0) @RequestParam(defaultValue = "0") page: Int,
        @Min(1) @Max(100) @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ListMyAnomalyEventsResponse> {
        val command = ListMyAnomalyEventsCommand(
            ownerUserId = principal.id,
            page = page,
            size = size,
        )
        return ResponseEntity.ok(listMyAnomalyEventsUseCase.execute(command))
    }

    @PostMapping("/{anomalyEventId}/false-positive")
    fun markFalsePositive(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable anomalyEventId: Long,
        @RequestBody request: MarkAnomalyFalsePositiveRequest,
    ): ResponseEntity<McpAnomalyEventResponse> {
        val command = MarkAnomalyFalsePositiveCommand(
            anomalyEventId = anomalyEventId,
            requestUserId = principal.id,
            note = request.note,
        )
        return ResponseEntity.ok(markAnomalyFalsePositiveUseCase.execute(command))
    }
}

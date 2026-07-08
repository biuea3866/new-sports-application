package com.sportsapp.presentation.dashboard.controller

import com.sportsapp.application.dashboard.dto.DashboardSummaryResponse
import com.sportsapp.application.dashboard.usecase.GetMyDashboardSummaryUseCase
import com.sportsapp.domain.user.vo.UserPrincipal
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/operator/dashboard")
@Profile("!test-jpa")
class OperatorDashboardApiController(
    private val getMyDashboardSummaryUseCase: GetMyDashboardSummaryUseCase,
) {
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('FACILITY_OWNER', 'EVENT_HOST', 'GOODS_SELLER')")
    fun getSummary(
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<DashboardSummaryResponse> {
        val response = getMyDashboardSummaryUseCase.execute(principal.id)
        return ResponseEntity.ok(response)
    }
}

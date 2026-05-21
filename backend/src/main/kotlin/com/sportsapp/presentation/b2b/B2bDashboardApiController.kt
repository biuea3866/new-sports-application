package com.sportsapp.presentation.b2b

import com.sportsapp.application.dashboard.DashboardSummaryResponse
import com.sportsapp.application.dashboard.GetMyDashboardSummaryUseCase
import com.sportsapp.domain.user.UserPrincipal
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/b2b/dashboard")
@Profile("!test-jpa")
class B2bDashboardApiController(
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

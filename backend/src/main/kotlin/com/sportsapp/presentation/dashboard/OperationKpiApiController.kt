package com.sportsapp.presentation.dashboard

import com.sportsapp.application.dashboard.GetOperationKpiCommand
import com.sportsapp.application.dashboard.GetOperationKpiResponse
import com.sportsapp.application.dashboard.GetOperationKpiUseCase
import com.sportsapp.domain.common.exceptions.InvalidRequestException
import com.sportsapp.domain.user.UserPrincipal
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

private const val MAX_PERIOD_DAYS = 365L

@RestController
@RequestMapping("/api/operator/dashboard")
class OperationKpiApiController(
    private val getOperationKpiUseCase: GetOperationKpiUseCase,
) {
    /**
     * 운영 통합 KPI 조회.
     *
     * IDOR 차단: [principal].id 를 ownerUserId 필터로 강제 — 운영자 본인 데이터만 반환.
     *
     * 기간 제약:
     * - from < to 필수
     * - 최대 365일
     * - 기본값(미지정) 시 from = 30일 전, to = 현재
     */
    @GetMapping("/kpi")
    @PreAuthorize("hasAnyRole('FACILITY_OWNER', 'EVENT_HOST', 'GOODS_SELLER', 'OPERATIONS_MANAGER')")
    fun getKpi(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        from: ZonedDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        to: ZonedDateTime?,
    ): ResponseEntity<GetOperationKpiResponse> {
        val now = ZonedDateTime.now()
        val resolvedFrom = from ?: now.minusDays(30)
        val resolvedTo = to ?: now
        validatePeriod(resolvedFrom, resolvedTo)

        val command = GetOperationKpiCommand(
            ownerUserId = principal.id,
            from = resolvedFrom,
            to = resolvedTo,
        )
        return ResponseEntity.ok(getOperationKpiUseCase.execute(command))
    }

    private fun validatePeriod(from: ZonedDateTime, to: ZonedDateTime) {
        if (!from.isBefore(to)) {
            throw InvalidRequestException("from must be before to")
        }
        val periodDays = ChronoUnit.DAYS.between(from, to)
        if (periodDays > MAX_PERIOD_DAYS) {
            throw InvalidRequestException("Period must not exceed $MAX_PERIOD_DAYS days, got $periodDays days")
        }
    }
}

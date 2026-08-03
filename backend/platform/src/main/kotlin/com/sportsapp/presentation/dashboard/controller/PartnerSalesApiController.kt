package com.sportsapp.presentation.dashboard.controller

import com.sportsapp.application.dashboard.dto.ListPartnerSalesCommand
import com.sportsapp.application.dashboard.dto.ListPartnerSalesResult
import com.sportsapp.application.dashboard.usecase.ListPartnerSalesUseCase
import com.sportsapp.domain.common.security.UserPrincipal
import com.sportsapp.domain.payment.entity.PaymentStatus
import org.springframework.data.domain.PageRequest
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.ZonedDateTime

/**
 * 파트너 매출·결제 내역 API.
 *
 * 구매자용 `/payments/me`와 스코프가 정반대다 — 이쪽은 **내가 판 건**의 결제를 반환한다.
 * IDOR 차단: 조회 대상 판매자를 [principal].id로 서버가 고정한다(클라이언트가 지정할 수 없다).
 */
@RestController
@RequestMapping("/api/operator/dashboard")
class PartnerSalesApiController(
    private val listPartnerSalesUseCase: ListPartnerSalesUseCase,
) {
    @GetMapping("/sales")
    @PreAuthorize("hasAnyRole('FACILITY_OWNER', 'EVENT_HOST', 'GOODS_SELLER', 'OPERATIONS_MANAGER')")
    fun listSales(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) status: PaymentStatus?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        paidAtFrom: ZonedDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        paidAtTo: ZonedDateTime?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ListPartnerSalesResult> {
        val command = ListPartnerSalesCommand(
            ownerUserId = principal.id,
            status = status,
            paidAtFrom = paidAtFrom,
            paidAtTo = paidAtTo,
            pageable = PageRequest.of(page, size),
        )
        return ResponseEntity.ok(listPartnerSalesUseCase.execute(command))
    }
}

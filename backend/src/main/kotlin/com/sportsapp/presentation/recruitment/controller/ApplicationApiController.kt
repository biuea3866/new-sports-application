package com.sportsapp.presentation.recruitment.controller

import com.sportsapp.application.recruitment.dto.ApplicationDetailResponse
import com.sportsapp.application.recruitment.dto.ApplicationResponse
import com.sportsapp.application.recruitment.dto.CancelApplicationCommand
import com.sportsapp.application.recruitment.usecase.CancelApplicationUseCase
import com.sportsapp.application.recruitment.usecase.GetApplicationDetailUseCase
import com.sportsapp.application.recruitment.usecase.ListMyApplicationsUseCase
import com.sportsapp.domain.user.vo.UserPrincipal
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 신청자 본인 관점 REST 계약 (FR-4). `/recruitments/{id}/applications`(개설자 관점 목록·신청)와
 * 달리 인증 principal(JWT `@AuthenticationPrincipal UserPrincipal`) 기준 본인 신청만 다룬다.
 */
@RestController
@RequestMapping("/applications")
class ApplicationApiController(
    private val listMyApplicationsUseCase: ListMyApplicationsUseCase,
    private val cancelApplicationUseCase: CancelApplicationUseCase,
    private val getApplicationDetailUseCase: GetApplicationDetailUseCase,
) {
    @GetMapping
    fun listMine(
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<List<ApplicationResponse>> =
        ResponseEntity.ok(listMyApplicationsUseCase.execute(principal.id))

    // Option A(주문 상세 화면 신설, 사용자 확정 2026-07-09) — 통합 주문내역의 RECRUITMENT 주문상세
    // 화면이 호출하는 단건 조회. 근거: `20260708-상품주문-공유상위컨텍스트-tdd.md` v1.3
    // "주문 항목 탭 네비게이션 결정" 절. 본인 소유 검증은 UseCase → DomainService로 위임.
    @GetMapping("/{id}")
    fun detail(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ): ResponseEntity<ApplicationDetailResponse> =
        ResponseEntity.ok(getApplicationDetailUseCase.execute(applicationId = id, requesterUserId = principal.id))

    @PostMapping("/{id}/cancel")
    fun cancel(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ): ResponseEntity<ApplicationResponse> {
        val response = cancelApplicationUseCase.execute(
            CancelApplicationCommand(applicationId = id, applicantUserId = principal.id),
        )
        return ResponseEntity.ok(response)
    }
}

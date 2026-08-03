package com.sportsapp.presentation.recruitment.controller

import com.sportsapp.application.recruitment.dto.InternalRecruitmentApplicationHistoryResponse
import com.sportsapp.application.recruitment.usecase.ListRecruitmentApplicationsForOrderHistoryUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private const val INTERNAL_AUTH_SUBJECT_HEADER = "X-Internal-Auth-Subject"

/**
 * 통합 주문내역(BE-08)이 fan-out 하는 recruitment 신청 이력 원격 공급 엔드포인트 (S2-05, edge
 * `OrderHistoryGateway.findRecruitmentOrders`의 2단계 구현 대상).
 *
 * 개인 데이터라 [INTERNAL_AUTH_SUBJECT_HEADER]가 필수다 — `/internal` 경로에는 JWT 가 오지 않고,
 * edge 가 이미 검증한 신원을 이 헤더로 전파한다(W1-06b 내부 신원 전파 계약). 헤더가 없으면 Spring
 * 이 `MissingRequestHeaderException`을 던지고 `GlobalExceptionHandler`가 400으로 매핑한다.
 * `/internal` 하위 전체 경로의 인가 규칙·호출자 인증은 S2-07 이 일괄 처리한다.
 */
@RestController
@RequestMapping("/internal/order-history/recruitment-applications")
class InternalRecruitmentApplicationApiController(
    private val listRecruitmentApplicationsForOrderHistoryUseCase: ListRecruitmentApplicationsForOrderHistoryUseCase,
) {
    @GetMapping
    fun list(
        @RequestHeader(INTERNAL_AUTH_SUBJECT_HEADER) subject: String,
    ): ResponseEntity<List<InternalRecruitmentApplicationHistoryResponse>> {
        val applicantUserId = subject.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid $INTERNAL_AUTH_SUBJECT_HEADER header value: $subject")
        return ResponseEntity.ok(listRecruitmentApplicationsForOrderHistoryUseCase.execute(applicantUserId))
    }
}

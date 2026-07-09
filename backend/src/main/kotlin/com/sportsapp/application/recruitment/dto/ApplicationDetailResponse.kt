package com.sportsapp.application.recruitment.dto

import com.sportsapp.domain.recruitment.dto.ApplicationDetail
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * 신청 단건 상세 응답 — 통합 주문내역(RECRUITMENT 탭)에서 이동하는 주문상세 화면 계약.
 */
data class ApplicationDetailResponse(
    val applicationId: Long,
    val recruitmentId: Long,
    val recruitmentTitle: String,
    val status: ApplicationStatus,
    val feeAmount: BigDecimal,
    val paymentId: Long?,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun of(detail: ApplicationDetail): ApplicationDetailResponse = ApplicationDetailResponse(
            applicationId = detail.applicationId,
            recruitmentId = detail.recruitmentId,
            recruitmentTitle = detail.recruitmentTitle,
            status = detail.status,
            feeAmount = detail.feeAmount,
            paymentId = detail.paymentId,
            createdAt = detail.createdAt,
        )
    }
}

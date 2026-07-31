package com.sportsapp.domain.recruitment.dto

import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * 신청 단건 상세 조회 결과 — 모집명(title)·참가비(feeAmount)를 조인한 프로젝션
 * (통합 주문내역 RECRUITMENT 탭 → 주문상세 GET /applications/{id}).
 */
data class ApplicationDetail(
    val applicationId: Long,
    val recruitmentId: Long,
    val recruitmentTitle: String,
    val status: ApplicationStatus,
    val feeAmount: BigDecimal,
    val paymentId: Long?,
    val createdAt: ZonedDateTime,
)

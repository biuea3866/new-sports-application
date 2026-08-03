package com.sportsapp.application.dashboard.dto

import com.sportsapp.domain.payment.entity.PaymentStatus
import org.springframework.data.domain.Pageable
import java.time.ZonedDateTime

/**
 * 파트너 매출 내역 조회 파라미터.
 *
 * [ownerUserId]는 **판매자**다 — 구매자 스코프(`/payments/me`)의 userId와 반대 개념이라
 * 커맨드 타입을 분리해 혼동을 막는다.
 */
data class ListPartnerSalesCommand(
    val ownerUserId: Long,
    val status: PaymentStatus?,
    val paidAtFrom: ZonedDateTime?,
    val paidAtTo: ZonedDateTime?,
    val pageable: Pageable,
)

package com.sportsapp.application.recruitment.dto

import com.sportsapp.domain.recruitment.dto.ApplicationWithRecruitmentTitle
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * 통합 주문내역(BE-08)이 fan-out 하는 recruitment 신청 이력 원격 공급 응답 (S2-05).
 *
 * edge `OrderHistoryGateway.findRecruitmentOrders`가 2단계에 이 응답을 그대로 소비한다 — 필드는
 * edge 소유 `OrderHistoryItem`(S2-01)이 `ApplicationWithRecruitmentTitle.toOrderHistoryItem()`으로
 * 채우는 값에서 역산했다. `detailPath` 조립은 edge 파사드의 책임이라 이 응답에는 포함하지 않는다.
 *
 * [amount]는 recruitment 자기 데이터(참가비)다 — edge·payment 역참조 없이 주문내역에 금액을
 * 노출하기 위해 공급자가 채운다. 무료 모임은 `0`(확정값)이고, 금액을 확정할 수 없는 경우만
 * `null`이다 — 둘을 구분해야 하므로 0으로 방어하지 않는다.
 */
data class InternalRecruitmentApplicationHistoryResponse(
    val sourceId: Long,
    val title: String,
    val status: ApplicationStatus,
    val paymentId: Long?,
    val createdAt: ZonedDateTime,
    val amount: BigDecimal?,
) {
    companion object {
        fun of(application: ApplicationWithRecruitmentTitle): InternalRecruitmentApplicationHistoryResponse =
            InternalRecruitmentApplicationHistoryResponse(
                sourceId = application.applicationId,
                title = application.recruitmentTitle,
                status = application.status,
                paymentId = application.paymentId,
                createdAt = application.createdAt,
                amount = application.feeAmount,
            )
    }
}

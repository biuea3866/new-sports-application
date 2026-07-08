package com.sportsapp.application.recruitment.dto

import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import java.time.ZonedDateTime

data class ApplyRecruitmentResult(
    val id: Long,
    val recruitmentId: Long,
    val status: ApplicationStatus,
    val paymentId: Long?,
    val checkoutUrl: String?,
    val appliedAt: ZonedDateTime,
) {
    companion object {
        fun of(
            application: Application,
            paymentId: Long? = application.paymentId,
            checkoutUrl: String? = null,
        ): ApplyRecruitmentResult = ApplyRecruitmentResult(
            id = application.id,
            recruitmentId = application.recruitmentId,
            status = application.status,
            paymentId = paymentId,
            checkoutUrl = checkoutUrl,
            appliedAt = application.createdAt,
        )
    }
}

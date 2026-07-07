package com.sportsapp.presentation.recruitment.dto.request

import com.sportsapp.application.recruitment.dto.ApplyRecruitmentCommand
import com.sportsapp.domain.payment.vo.PaymentMethod

data class ApplyRecruitmentRequest(
    val paymentMethod: PaymentMethod,
    val currency: String,
) {
    fun toCommand(recruitmentId: Long, applicantUserId: Long): ApplyRecruitmentCommand = ApplyRecruitmentCommand(
        recruitmentId = recruitmentId,
        applicantUserId = applicantUserId,
        paymentMethod = paymentMethod,
        currency = currency,
    )
}

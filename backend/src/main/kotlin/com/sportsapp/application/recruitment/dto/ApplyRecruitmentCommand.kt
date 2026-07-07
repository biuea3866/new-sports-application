package com.sportsapp.application.recruitment.dto

import com.sportsapp.domain.payment.vo.PaymentMethod

data class ApplyRecruitmentCommand(
    val recruitmentId: Long,
    val applicantUserId: Long,
    val paymentMethod: PaymentMethod,
    val currency: String,
)

package com.sportsapp.application.recruitment.dto

import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import java.time.ZonedDateTime

data class ApplicationResponse(
    val id: Long,
    val recruitmentId: Long,
    val status: ApplicationStatus,
    val paymentId: Long?,
    val appliedAt: ZonedDateTime,
) {
    companion object {
        fun of(application: Application): ApplicationResponse = ApplicationResponse(
            id = application.id,
            recruitmentId = application.recruitmentId,
            status = application.status,
            paymentId = application.paymentId,
            appliedAt = application.createdAt,
        )
    }
}

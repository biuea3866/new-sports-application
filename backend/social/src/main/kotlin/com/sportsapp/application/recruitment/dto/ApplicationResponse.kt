package com.sportsapp.application.recruitment.dto

import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import java.time.ZonedDateTime

/**
 * `applicantUserId`는 신청자 목록 화면(개설자 전용)이 "누가 신청했는가"를 표시하는 데 쓴다 —
 * 이 필드가 없어 화면이 신청 행 PK(`신청 #5`)를 대신 노출하고 있었다.
 */
data class ApplicationResponse(
    val id: Long,
    val recruitmentId: Long,
    val applicantUserId: Long,
    val status: ApplicationStatus,
    val paymentId: Long?,
    val appliedAt: ZonedDateTime,
) {
    companion object {
        fun of(application: Application): ApplicationResponse = ApplicationResponse(
            id = application.id,
            recruitmentId = application.recruitmentId,
            applicantUserId = application.applicantUserId,
            status = application.status,
            paymentId = application.paymentId,
            appliedAt = application.createdAt,
        )
    }
}

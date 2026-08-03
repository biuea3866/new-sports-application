package com.sportsapp.application.recruitment.dto

import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import java.time.ZonedDateTime

/**
 * 개설자 관점 신청자 목록 항목. 신청자 본인 관점([ApplicationResponse])과 달리 "누가 신청했는가"가
 * 핵심이라 신청자 id 와 표시 이름을 함께 싣는다.
 *
 * 표시 이름은 user 컨텍스트 소유라 recruitment 도메인이 알 수 없다 — 두 컨텍스트를 모두 아는
 * application 레이어(ListApplicationsUseCase)가 조회해 넘긴다.
 */
data class RecruitmentApplicantResponse(
    val id: Long,
    val recruitmentId: Long,
    val applicantUserId: Long,
    val applicantDisplayName: String,
    val status: ApplicationStatus,
    val appliedAt: ZonedDateTime,
) {
    companion object {
        fun of(application: Application, applicantDisplayName: String): RecruitmentApplicantResponse =
            RecruitmentApplicantResponse(
                id = application.id,
                recruitmentId = application.recruitmentId,
                applicantUserId = application.applicantUserId,
                applicantDisplayName = applicantDisplayName,
                status = application.status,
                appliedAt = application.createdAt,
            )
    }
}

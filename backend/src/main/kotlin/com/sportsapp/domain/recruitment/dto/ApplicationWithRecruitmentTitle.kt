package com.sportsapp.domain.recruitment.dto

import com.sportsapp.domain.recruitment.entity.ApplicationStatus

/**
 * 사용자별 Application 조회 결과 — 표시명(모집 제목) 조인 프로젝션.
 * 참조 Recruitment가 없거나 삭제된 경우 [recruitmentTitle]은 빈 문자열로 방어된다.
 */
data class ApplicationWithRecruitmentTitle(
    val applicationId: Long,
    val status: ApplicationStatus,
    val recruitmentTitle: String,
)

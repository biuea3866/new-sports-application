package com.sportsapp.domain.recruitment.dto

import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import java.time.ZonedDateTime

/**
 * 사용자별 Application 조회 결과 — 표시명(모집 제목) 조인 프로젝션.
 * 참조 Recruitment가 없거나 삭제된 경우 [recruitmentTitle]은 빈 문자열로 방어된다.
 *
 * [paymentId]·[createdAt]은 order 통합조회(BE-08)의 결제 연계 노출·`createdAt desc` 병합에
 * 쓰인다 — 둘 다 Application 자기 컬럼이라 추가 조인 없이 노출한다.
 */
data class ApplicationWithRecruitmentTitle(
    val applicationId: Long,
    val status: ApplicationStatus,
    val recruitmentTitle: String,
    val paymentId: Long?,
    val createdAt: ZonedDateTime,
)

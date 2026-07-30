package com.sportsapp.domain.recruitment.repository

import com.sportsapp.domain.recruitment.dto.ApplicationWithRecruitmentTitle

/**
 * order 통합조회용 Application 조회 (BE-06).
 *
 * applications → recruitments(동일 recruitment 컨텍스트) 조인으로 모집명(title)을 포함해 반환한다.
 */
interface ApplicationCustomRepository {
    fun findBy(applicantUserId: Long): List<ApplicationWithRecruitmentTitle>
}

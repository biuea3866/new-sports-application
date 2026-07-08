package com.sportsapp.domain.recruitment.repository

import com.sportsapp.domain.recruitment.entity.Recruitment
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * catalog 통합 검색용 Recruitment 조회 (BE-06).
 *
 * status=OPEN 고정 + keyword(title) 부분 일치. CLOSED/CANCELLED는 제외한다.
 */
interface RecruitmentCustomRepository {
    fun searchOpen(keyword: String?, pageable: Pageable): Page<Recruitment>
}

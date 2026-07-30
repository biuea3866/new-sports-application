package com.sportsapp.domain.facility.repository

import com.sportsapp.domain.facility.entity.Program
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * catalog 통합 검색용 Program 조회 (BE-05).
 *
 * Program은 status enum이 없으므로 미삭제(deleted_at IS NULL) 전량이 조회 대상이다.
 */
interface ProgramCustomRepository {
    fun searchForCatalog(keyword: String?, pageable: Pageable): Page<Program>
}

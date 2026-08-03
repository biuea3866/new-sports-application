package com.sportsapp.domain.recruitment.repository

import com.sportsapp.domain.recruitment.entity.Recruitment

interface RecruitmentRepository {
    fun save(recruitment: Recruitment): Recruitment
    /** 발견·신청 대상 조회 — 소프트 삭제된 모집은 제외한다. */
    fun findById(id: Long): Recruitment?

    /**
     * 삭제 여부와 무관한 조회. **이미 성립한 신청의 생명주기**(취소·환불·주문상세)에만 쓴다 —
     * 모집이 삭제됐다고 사용자가 환불을 못 받으면 안 된다. 발견·신청 경로에는 쓰지 않는다.
     */
    fun findByIdIncludingDeleted(id: Long): Recruitment?
    fun findForUpdateById(id: Long): Recruitment?
    fun findAll(communityId: Long?): List<Recruitment>
}

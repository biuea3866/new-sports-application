package com.sportsapp.infrastructure.recruitment.mysql

import com.sportsapp.domain.recruitment.dto.ApplicationExpiryCandidate
import com.sportsapp.domain.recruitment.entity.Application
import java.time.ZonedDateTime

interface ApplicationQueryDslRepository {
    fun countActiveByRecruitmentId(recruitmentId: Long): Int
    fun findByApplicantUserId(applicantUserId: Long): List<Application>

    /** W1-11d 만료 스위퍼 청크 조회 — [com.sportsapp.domain.recruitment.repository.ApplicationRepository.findPendingCreatedBefore] 구현. */
    fun findPendingCreatedBefore(before: ZonedDateTime, afterId: Long, limit: Int): List<ApplicationExpiryCandidate>

    /** W1-11d 만료 스위퍼 CAS 쓰기 — [com.sportsapp.domain.recruitment.repository.ApplicationRepository.tryExpire] 구현. */
    fun tryExpire(applicationId: Long): Boolean
}

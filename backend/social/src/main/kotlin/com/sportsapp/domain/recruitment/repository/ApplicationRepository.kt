package com.sportsapp.domain.recruitment.repository

import com.sportsapp.domain.recruitment.dto.ApplicationExpiryCandidate
import com.sportsapp.domain.recruitment.entity.Application
import java.time.ZonedDateTime

interface ApplicationRepository {
    fun save(application: Application): Application
    fun findById(id: Long): Application?
    fun countActiveByRecruitmentId(recruitmentId: Long): Int
    fun findByRecruitmentId(recruitmentId: Long): List<Application>
    fun findConfirmedByRecruitmentId(recruitmentId: Long): List<Application>
    fun findByApplicantUserId(applicantUserId: Long): List<Application>

    /**
     * W1-11d 만료 스위퍼가 소비 — PENDING 상태·삭제되지 않았으며 createdAt이 before보다
     * 이르고 id가 afterId보다 큰 신청을 id 오름차순으로 최대 limit건 조회한다(청크 조회).
     * `facility-booking`(W1-11c) `BookingRepository.findPendingCreatedBefore`와 동일한
     * 이유로 createdAt도 함께 반환한다.
     */
    fun findPendingCreatedBefore(before: ZonedDateTime, afterId: Long, limit: Int): List<ApplicationExpiryCandidate>

    /**
     * W1-11d 만료 스위퍼 CAS 쓰기 — 현재 상태가 PENDING일 때만 CANCELLED로 원자적 전이한다
     * (조건부 UPDATE, WHERE status = PENDING). MySQL InnoDB는 UPDATE 평가 시 트랜잭션
     * 스냅샷이 아닌 최신 커밋본을 읽으므로(current read), 청크 트랜잭션이 REPEATABLE READ
     * 스냅샷을 뜬 이후 다른 트랜잭션(webhook 확정)이 커밋한 CONFIRMED를 CANCELLED로
     * 덮어쓰는 lost update가 발생하지 않는다. 신규 상태를 추가하지 않고 기존 CANCELLED를
     * 재사용한다(티켓 결정). 영향 행 수(affected rows > 0)로 성공 여부를 반환한다.
     */
    fun tryExpire(applicationId: Long): Boolean
}

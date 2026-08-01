package com.sportsapp.domain.recruitment.entity

import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.recruitment.exception.InvalidRecruitmentException
import com.sportsapp.domain.recruitment.exception.InvalidRecruitmentStateException
import com.sportsapp.domain.recruitment.exception.NotRecruiterException
import com.sportsapp.domain.recruitment.exception.RecruitmentApplicationClosedException
import com.sportsapp.domain.recruitment.exception.RecruitmentFullException
import com.sportsapp.domain.recruitment.exception.RecruitmentNotOpenException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.ZonedDateTime

@Entity
@Table(name = "recruitments")
class Recruitment private constructor(
    @Column(name = "title", nullable = false)
    val title: String,

    @Column(name = "description", nullable = true)
    val description: String?,

    @Column(name = "capacity", nullable = false)
    val capacity: Int,

    @Column(name = "fee_amount", nullable = false)
    val feeAmount: BigDecimal,

    @Column(name = "activity_at", nullable = false)
    val activityAt: ZonedDateTime,

    @Column(name = "application_deadline", nullable = false)
    val applicationDeadline: ZonedDateTime,

    @Column(name = "community_id", nullable = true)
    val communityId: Long?,

    @Column(name = "recruiter_user_id", nullable = false)
    val recruiterUserId: Long,

    initialStatus: RecruitmentStatus,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: RecruitmentStatus = initialStatus
        private set

    /**
     * 신청 가능 여부를 자기 상태로 판단해 검증한다. 모집중(OPEN)이 아니거나, 마감이 지났거나,
     * 정원이 가득 찼으면 각 사유에 맞는 예외를 던진다.
     *
     * ThrowsCount 억제 근거(W1-DEBT-01): 3개 가드 클로즈(open 상태·마감·정원)가 각자 다른
     * 실패 사유에 대응하는 서로 다른 도메인 예외를 던진다. 이 검증들을 별도 private 메서드로
     * 쪼개도 총 throw 횟수는 줄지 않고 지표만 우회하게 되며, 오히려 하나의 응집된 신청
     * 가능 여부 판정을 인위적으로 분산시킨다 — guard clause 권장 컨벤션에 부합하는 형태다.
     */
    @Suppress("ThrowsCount")
    fun requireApplicable(currentApplicantCount: Int) {
        if (status != RecruitmentStatus.OPEN) throw RecruitmentNotOpenException(id, status)
        if (isDeadlinePassed()) throw RecruitmentApplicationClosedException(id)
        if (currentApplicantCount >= capacity) throw RecruitmentFullException(id)
    }

    fun isFree(): Boolean = feeAmount.signum() == 0

    fun closeWhenFull(currentApplicantCount: Int) {
        if (status == RecruitmentStatus.OPEN && currentApplicantCount >= capacity) {
            status = RecruitmentStatus.CLOSED
        }
    }

    /**
     * W1-11d 만료 스위퍼 — PENDING 신청이 만료(CANCELLED)돼 정원에 여유가 생기면 CLOSED를
     * OPEN으로 되돌린다. [closeWhenFull]과 대칭인 캐시 무효화 메서드로, `RecruitmentStatus`의
     * 일반 상태 머신(`canTransitTo`)을 거치지 않고 이 필드만 직접 갱신한다(closeWhenFull과
     * 동일한 방식) — CLOSED가 아니거나(OPEN인데 정원이 남는 경우는 애초에 CLOSED된 적이
     * 없다) CANCELLED(개설자가 모집 자체를 취소)면 아무 것도 하지 않는다. 취소된 모집을
     * 되살리지 않는 것이 핵심 방어다.
     */
    fun reopenIfBelowCapacity(currentApplicantCount: Int) {
        if (status == RecruitmentStatus.CLOSED && currentApplicantCount < capacity) {
            status = RecruitmentStatus.OPEN
        }
    }

    fun cancelByHost(userId: Long) {
        requireRecruiter(userId)
        if (status == RecruitmentStatus.CANCELLED) {
            return
        }
        if (!status.canTransitTo(RecruitmentStatus.CANCELLED)) {
            throw InvalidRecruitmentStateException(status, RecruitmentStatus.CANCELLED)
        }
        status = RecruitmentStatus.CANCELLED
    }

    fun requireRecruiter(userId: Long) {
        if (recruiterUserId != userId) {
            throw NotRecruiterException(id)
        }
    }

    private fun isDeadlinePassed(): Boolean = ZonedDateTime.now().isAfter(applicationDeadline)

    companion object {
        private const val TITLE_MAX_LENGTH = 200

        fun create(
            title: String,
            capacity: Int,
            feeAmount: BigDecimal,
            activityAt: ZonedDateTime,
            applicationDeadline: ZonedDateTime,
            communityId: Long?,
            recruiterUserId: Long,
            description: String? = null,
        ): Recruitment {
            validateTitle(title)
            validateCapacity(capacity)
            validateFeeAmount(feeAmount)
            return Recruitment(
                title = title,
                description = description,
                capacity = capacity,
                feeAmount = feeAmount,
                activityAt = activityAt,
                applicationDeadline = applicationDeadline,
                communityId = communityId,
                recruiterUserId = recruiterUserId,
                initialStatus = RecruitmentStatus.OPEN,
            )
        }

        private fun validateTitle(title: String) {
            if (title.isBlank()) {
                throw InvalidRecruitmentException("title must not be blank")
            }
            if (title.length > TITLE_MAX_LENGTH) {
                throw InvalidRecruitmentException("title must not exceed $TITLE_MAX_LENGTH characters, got: ${title.length}")
            }
        }

        private fun validateCapacity(capacity: Int) {
            if (capacity <= 0) {
                throw InvalidRecruitmentException("capacity must be positive, got: $capacity")
            }
        }

        private fun validateFeeAmount(feeAmount: BigDecimal) {
            if (feeAmount < BigDecimal.ZERO) {
                throw InvalidRecruitmentException("feeAmount must not be negative, got: $feeAmount")
            }
        }

        fun reconstitute(
            title: String,
            description: String?,
            capacity: Int,
            feeAmount: BigDecimal,
            activityAt: ZonedDateTime,
            applicationDeadline: ZonedDateTime,
            communityId: Long?,
            recruiterUserId: Long,
            status: RecruitmentStatus,
        ): Recruitment = Recruitment(
            title = title,
            description = description,
            capacity = capacity,
            feeAmount = feeAmount,
            activityAt = activityAt,
            applicationDeadline = applicationDeadline,
            communityId = communityId,
            recruiterUserId = recruiterUserId,
            initialStatus = status,
        )
    }
}

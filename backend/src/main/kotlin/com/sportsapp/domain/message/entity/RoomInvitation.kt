package com.sportsapp.domain.message.entity

import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.message.exception.InvitationNotTransitionableException
import com.sportsapp.domain.message.exception.NotInvitationTargetException
import com.sportsapp.domain.message.vo.InvitationStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * 방 초대(게스트 초대 상태 전이 관리) — TDD "RoomInvitation"(신규), 상태 전이 표 "RoomInvitation".
 *
 * [RoomParticipant]와 동일하게 `private constructor` + `create`/`reconstitute` 팩토리 패턴을 따른다.
 */
@Entity
@Table(name = "room_invitations")
class RoomInvitation private constructor(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    val room: Room,

    @Column(name = "inviter_user_id", nullable = false)
    val inviterUserId: Long,

    @Column(name = "invitee_user_id", nullable = false)
    val inviteeUserId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private var status: InvitationStatus,

    @Column(name = "can_speak", nullable = false)
    val canSpeak: Boolean,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: ZonedDateTime,

    @Column(name = "responded_at")
    private var respondedAt: ZonedDateTime?,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    val currentStatus: InvitationStatus get() = status
    val currentRespondedAt: ZonedDateTime? get() = respondedAt

    /** 초대 대상(invitee) 본인인지 검증 — accept/reject 호출자 검증에 사용한다. */
    fun validateInvitee(userId: Long) {
        if (inviteeUserId != userId) throw NotInvitationTargetException(userId, id)
    }

    fun accept() = transitionTo(InvitationStatus.ACCEPTED)

    fun reject() = transitionTo(InvitationStatus.REJECTED)

    fun revoke() = transitionTo(InvitationStatus.REVOKED)

    fun expire() = transitionTo(InvitationStatus.EXPIRED)

    /**
     * 수락 시 [RoomParticipant.forGuest]에 넘길 잔여 만료 일수 — 절대 시각([expiresAt])을
     * `forGuest`가 요구하는 상대 일수로 환산한다(호출 시점 기준). 이미 지났으면 최소 1일을 보장한다.
     */
    fun remainingExpiryDays(): Long {
        val hoursRemaining = ChronoUnit.HOURS.between(ZonedDateTime.now(), expiresAt)
        if (hoursRemaining <= 0) return 1L
        return (hoursRemaining / HOURS_PER_DAY) + 1
    }

    private fun transitionTo(next: InvitationStatus) {
        if (!status.canTransitTo(next)) throw InvitationNotTransitionableException(id, status, next)
        status = next
        respondedAt = ZonedDateTime.now()
    }

    companion object {
        private const val HOURS_PER_DAY = 24

        /** 신규 초대 생성 — 만료 시각은 호출 시점 기준 now+expiresInDays로 메서드 내부에서 해결한다. */
        fun create(
            room: Room,
            inviterUserId: Long,
            inviteeUserId: Long,
            canSpeak: Boolean,
            expiresInDays: Long,
        ): RoomInvitation {
            require(expiresInDays > 0) { "expiresInDays must be positive" }
            return RoomInvitation(
                room = room,
                inviterUserId = inviterUserId,
                inviteeUserId = inviteeUserId,
                status = InvitationStatus.PENDING,
                canSpeak = canSpeak,
                expiresAt = ZonedDateTime.now().plusDays(expiresInDays),
                respondedAt = null,
            )
        }

        /** 영속화 계층 복원 — 검증 없이 필드를 그대로 복구한다. */
        fun reconstitute(
            room: Room,
            inviterUserId: Long,
            inviteeUserId: Long,
            status: InvitationStatus,
            canSpeak: Boolean,
            expiresAt: ZonedDateTime,
            respondedAt: ZonedDateTime?,
        ): RoomInvitation = RoomInvitation(
            room = room,
            inviterUserId = inviterUserId,
            inviteeUserId = inviteeUserId,
            status = status,
            canSpeak = canSpeak,
            expiresAt = expiresAt,
            respondedAt = respondedAt,
        )
    }
}

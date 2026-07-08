package com.sportsapp.domain.message.entity

import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.message.exception.ReadOnlyParticipantException
import com.sportsapp.domain.message.exception.RoomParticipantExpiredException
import com.sportsapp.domain.message.vo.ParticipantType
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

/**
 * 방 참여자 — 참여 스코프(정회원/게스트)와 읽음 커서를 캡슐화한다 (TDD "Guest", FR-7/13/14).
 *
 * [Alert][com.sportsapp.domain.alerting.entity.Alert]와 동일하게 `private constructor` +
 * `create`/`forGuest`/`reconstitute` 팩토리 패턴을 따른다.
 */
@Entity
@Table(name = "room_participants")
class RoomParticipant private constructor(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    val room: Room,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "joined_at", nullable = false)
    val joinedAt: ZonedDateTime,

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_type", nullable = false, length = 20)
    val participantType: ParticipantType,

    @Column(name = "can_speak", nullable = false)
    val canSpeak: Boolean,

    @Column(name = "expires_at")
    val expiresAt: ZonedDateTime?,

    @Column(name = "last_read_message_id")
    private var lastReadMessageId: Long?,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    val currentLastReadMessageId: Long? get() = lastReadMessageId

    /**
     * 읽음 커서 전진 — forward-only 단조 증가. 멀티 디바이스에서 이전 값보다 작거나 같은
     * messageId 가 뒤늦게 도착해도 역행하지 않는다.
     */
    fun markReadUpTo(messageId: Long) {
        val current = lastReadMessageId
        if (current == null || messageId > current) {
            lastReadMessageId = messageId
        }
    }

    /** 발화 권한 검증 — 읽기 전용 게스트(canSpeak=false)는 예외. */
    fun validateCanSpeak() {
        if (!canSpeak) throw ReadOnlyParticipantException(userId = userId, roomId = room.id)
    }

    /** 만료 검증 — 게스트 만료 시각이 지났으면 예외. expiresAt=null(MEMBER)은 항상 통과. */
    fun validateNotExpired() {
        val expiry = expiresAt ?: return
        if (ZonedDateTime.now().isAfter(expiry)) {
            throw RoomParticipantExpiredException(userId = userId, roomId = room.id)
        }
    }

    /** 정회원(MEMBER) 여부 질의 — 호출부가 내부 상태(participantType)를 직접 비교하지 않도록 캡슐화한다. */
    fun isMember(): Boolean = participantType == ParticipantType.MEMBER

    /**
     * 방출 표시 — GUEST 참여자만 방출 대상이 될 수 있다. 실제 소프트 삭제(deletedAt 기록)는
     * 호출자(방장) 검증을 마친 DomainService가 [softDelete]로 수행한다.
     */
    fun evict() {
        require(participantType == ParticipantType.GUEST) { "Only guest participants can be evicted" }
    }

    companion object {
        /** 정회원 참여 생성 — MEMBER·발화 가능·무기한(expiresAt=null). 기존 호출부 호환. */
        fun create(room: Room, userId: Long): RoomParticipant = RoomParticipant(
            room = room,
            userId = userId,
            joinedAt = ZonedDateTime.now(),
            participantType = ParticipantType.MEMBER,
            canSpeak = true,
            expiresAt = null,
            lastReadMessageId = null,
        )

        /** 게스트 참여 생성 — 만료 시각은 호출 시점 기준 now+expiresInDays 로 메서드 내부에서 해결한다. */
        fun forGuest(room: Room, userId: Long, canSpeak: Boolean, expiresInDays: Long): RoomParticipant {
            require(expiresInDays > 0) { "expiresInDays must be positive" }
            return RoomParticipant(
                room = room,
                userId = userId,
                joinedAt = ZonedDateTime.now(),
                participantType = ParticipantType.GUEST,
                canSpeak = canSpeak,
                expiresAt = ZonedDateTime.now().plusDays(expiresInDays),
                lastReadMessageId = null,
            )
        }

        /** 영속화 계층 복원 — 검증 없이 필드를 그대로 복구한다. */
        fun reconstitute(
            room: Room,
            userId: Long,
            joinedAt: ZonedDateTime,
            participantType: ParticipantType,
            canSpeak: Boolean,
            expiresAt: ZonedDateTime?,
            lastReadMessageId: Long?,
        ): RoomParticipant = RoomParticipant(
            room = room,
            userId = userId,
            joinedAt = joinedAt,
            participantType = participantType,
            canSpeak = canSpeak,
            expiresAt = expiresAt,
            lastReadMessageId = lastReadMessageId,
        )
    }
}

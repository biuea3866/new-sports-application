package com.sportsapp.domain.message.entity

import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.message.exception.NotRoomHostException
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.message.vo.RoomType
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "rooms")
class Room(
    @Column(name = "type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    val type: RoomType,
    @Column(name = "name", length = 100)
    var name: String?,
    @Column(name = "context_type", length = 30)
    @Enumerated(EnumType.STRING)
    val contextType: RoomContextType?,
    @Column(name = "context_id")
    val contextId: Long?,
    @Column(name = "host_user_id")
    private var hostUserId: Long?,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    @Column(name = "last_message_at")
    var lastMessageAt: ZonedDateTime? = null
        private set

    @OneToMany(mappedBy = "room", cascade = [CascadeType.PERSIST, CascadeType.MERGE], fetch = FetchType.LAZY)
    val messages: MutableList<Message> = mutableListOf()

    @OneToMany(mappedBy = "room", cascade = [CascadeType.PERSIST, CascadeType.MERGE], fetch = FetchType.LAZY)
    val participants: MutableList<RoomParticipant> = mutableListOf()

    fun validateNotDeleted() {
        check(!isDeleted) { "Room is already deleted" }
    }

    fun lastMessageBumpedTo(sentAt: ZonedDateTime) {
        lastMessageAt = sentAt
    }

    fun belongsToContext(): Boolean = contextType != null && contextId != null

    /** 현재 방장 user_id — 방장 개념이 없는 방(DIRECT 등)은 null(BE-13). */
    val currentHostUserId: Long? get() = hostUserId

    /** 방장 여부 질의 — 호출부가 내부 상태(hostUserId)를 직접 비교하지 않도록 캡슐화한다. */
    fun isHostedBy(userId: Long): Boolean = hostUserId == userId

    /**
     * 방장 전용 행위(게스트 초대·초대 철회·수동 방출 등) 검증 — 아니면 예외.
     * [GuestInvitationDomainService][com.sportsapp.domain.message.service.GuestInvitationDomainService]와
     * [GuestEvictionDomainService][com.sportsapp.domain.message.service.GuestEvictionDomainService]가
     * 이 단일 판정을 공유한다(BE-13, 과거 참여자 유형별 추론 비대칭 제거).
     */
    fun requireHostedBy(userId: Long) {
        if (!isHostedBy(userId)) throw NotRoomHostException(userId, id)
    }

    /** 방장 지정·교체 — provision·그룹 방 생성·거래 방 생성 시점에 명시적으로 호출한다. */
    fun assignHost(userId: Long) {
        this.hostUserId = userId
    }

    companion object {
        fun createDirect(): Room =
            Room(type = RoomType.DIRECT, name = null, contextType = null, contextId = null, hostUserId = null)

        fun createGroup(name: String, hostUserId: Long? = null): Room {
            require(name.isNotBlank()) { "Group room name must not be blank" }
            return Room(type = RoomType.GROUP, name = name, contextType = null, contextId = null, hostUserId = hostUserId)
        }

        fun createForContext(
            type: RoomType,
            contextType: RoomContextType,
            contextId: Long,
            name: String?,
            hostUserId: Long? = null,
        ): Room {
            if (name != null) {
                require(name.isNotBlank()) { "Group room name must not be blank" }
            }
            return Room(type = type, name = name, contextType = contextType, contextId = contextId, hostUserId = hostUserId)
        }
    }
}

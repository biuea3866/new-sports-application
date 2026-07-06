package com.sportsapp.domain.community.entity

import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.community.event.CommunityCreatedEvent
import com.sportsapp.domain.community.exception.NotCommunityHostException
import com.sportsapp.domain.community.vo.CommunityVisibility
import com.sportsapp.domain.common.vo.SportCategory
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient

/**
 * 커뮤니티 애그리거트 (TDD FR-1/2/3, Detail Design "Community").
 *
 * [Booking][com.sportsapp.domain.booking.entity.Booking]과 동일하게 `JpaAuditingBase` 를 상속하면서
 * `@Transient` domainEvents 리스트를 직접 관리한다(`AggregateRoot`는 audit 컬럼이 없는 엔티티 전용).
 */
@Entity
@Table(name = "communities")
class Community private constructor(
    @Column(name = "name", nullable = false, length = 100)
    val name: String,

    @Column(name = "description", length = 500)
    val description: String?,

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    val visibility: CommunityVisibility,

    @Enumerated(EnumType.STRING)
    @Column(name = "sport_category", nullable = false, length = 30)
    val sportCategory: SportCategory,

    @Column(name = "host_user_id", nullable = false)
    private var hostUserId: Long,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    @Transient
    private var _domainEvents: MutableList<DomainEvent>? = null

    private val domainEvents: MutableList<DomainEvent>
        get() = _domainEvents ?: mutableListOf<DomainEvent>().also { _domainEvents = it }

    val currentHostUserId: Long get() = hostUserId

    /** 공개 여부 질의 — 공개 커뮤니티는 즉시 가입, 비공개는 방장 승인 후 가입된다 (FR-2). */
    fun isPublic(): Boolean = visibility == CommunityVisibility.PUBLIC

    /** 요청자가 현재 방장인지 검증 — 아니면 예외 (승인·강퇴·위임 공통 가드). */
    fun requireHost(userId: Long) {
        if (userId != hostUserId) throw NotCommunityHostException(id, userId)
    }

    /** 방장 권한을 [newHostUserId]에게 위임한다 (FR-3). */
    fun transferHostTo(newHostUserId: Long) {
        this.hostUserId = newHostUserId
    }

    fun pullDomainEvents(): List<DomainEvent> {
        val events = domainEvents.toList()
        domainEvents.clear()
        return events
    }

    /**
     * 개설 이벤트를 적재한다 — `create()` 시점(IDENTITY 미할당, id=0)이 아니라
     * DomainService가 `save()`로 id를 확정한 **이후** 호출해야 한다 (리뷰 p2-②).
     * `AbstractDomainEvent.aggregateId`는 불변 값이라 save 전에 미리 만들어 두면
     * BE-09 컨텍스트 방 provisioning이 소비할 communityId가 영원히 0으로 고정된다.
     */
    fun registerCreatedEvent() {
        registerEvent(CommunityCreatedEvent(communityId = id, hostUserId = hostUserId, name = name))
    }

    private fun registerEvent(event: DomainEvent) {
        domainEvents.add(event)
    }

    companion object {
        /**
         * 신규 커뮤니티 개설 — 개설자가 곧 최초 방장(hostUserId)이 된다.
         * id가 아직 미확정이므로 이벤트는 여기서 적재하지 않는다 — DomainService가
         * save() 이후 [registerCreatedEvent]를 호출한다 (리뷰 p2-②).
         */
        fun create(
            name: String,
            description: String?,
            visibility: CommunityVisibility,
            sportCategory: SportCategory,
            hostUserId: Long,
        ): Community {
            require(name.isNotBlank()) { "Community name must not be blank" }
            return Community(
                name = name,
                description = description,
                visibility = visibility,
                sportCategory = sportCategory,
                hostUserId = hostUserId,
            )
        }

        /** 영속화 계층 복원 — 검증 없이 필드를 그대로 복구한다. */
        fun reconstitute(
            name: String,
            description: String?,
            visibility: CommunityVisibility,
            sportCategory: SportCategory,
            hostUserId: Long,
        ): Community = Community(
            name = name,
            description = description,
            visibility = visibility,
            sportCategory = sportCategory,
            hostUserId = hostUserId,
        )
    }
}

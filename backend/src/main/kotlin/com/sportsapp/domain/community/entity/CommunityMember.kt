package com.sportsapp.domain.community.entity

import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.community.event.CommunityMemberJoinedEvent
import com.sportsapp.domain.community.event.CommunityMemberLeftEvent
import com.sportsapp.domain.community.exception.CannotKickHostException
import com.sportsapp.domain.community.exception.HostMustTransferBeforeLeaveException
import com.sportsapp.domain.community.exception.InvalidMembershipStateException
import com.sportsapp.domain.community.vo.CommunityRole
import com.sportsapp.domain.community.vo.MembershipStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
import java.time.ZonedDateTime

/**
 * 커뮤니티 멤버십 애그리거트 (TDD Detail Design "CommunityMember", 상태 전이 표).
 *
 * 역할(role)·상태(status) 전이를 캡슐화한다 — 강퇴 대상이 방장인지, 탈퇴 시도자가 방장인지는
 * 자기 자신의 [role] 필드로 판단한다(Tell, Don't Ask — 호출부는 방장 위임 관계를 몰라도 된다).
 * 방장 여부에 따른 승인·강퇴 **요청자** 인가는 [Community.requireHost]가 담당한다.
 */
@Entity
@Table(name = "community_members")
class CommunityMember private constructor(
    @Column(name = "community_id", nullable = false)
    val communityId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private var role: CommunityRole,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private var status: MembershipStatus,

    @Column(name = "joined_at")
    private var joinedAt: ZonedDateTime?,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    @Transient
    private var _domainEvents: MutableList<DomainEvent>? = null

    private val domainEvents: MutableList<DomainEvent>
        get() = _domainEvents ?: mutableListOf<DomainEvent>().also { _domainEvents = it }

    val currentRole: CommunityRole get() = role
    val currentStatus: MembershipStatus get() = status
    val currentJoinedAt: ZonedDateTime? get() = joinedAt

    /** 방장 승인 — PENDING_APPROVAL → ACTIVE. 승인 요청자가 방장인지는 DomainService가 [Community.requireHost]로 먼저 검증한다. */
    fun approve() {
        transitTo(MembershipStatus.ACTIVE)
        joinedAt = ZonedDateTime.now()
        registerEvent(CommunityMemberJoinedEvent(memberId = id, communityId = communityId, userId = userId))
    }

    /** 강퇴 — 대상이 방장(HOST) 본인이면 거부한다 (TDD 상태 전이 표). */
    fun kick() {
        if (role == CommunityRole.HOST) throw CannotKickHostException(communityId, userId)
        transitTo(MembershipStatus.KICKED)
        registerEvent(CommunityMemberLeftEvent(memberId = id, communityId = communityId, userId = userId))
    }

    /** 탈퇴 — 방장은 위임 없이 탈퇴할 수 없다 (TDD 상태 전이 표). */
    fun leave() {
        if (role == CommunityRole.HOST) throw HostMustTransferBeforeLeaveException(communityId, userId)
        transitTo(MembershipStatus.LEFT)
        registerEvent(CommunityMemberLeftEvent(memberId = id, communityId = communityId, userId = userId))
    }

    /** 방장 권한 위임 시 신규 방장에게 호출한다. */
    fun promoteToHost() {
        role = CommunityRole.HOST
    }

    /** 방장 권한 위임 시 기존 방장에게 호출한다. */
    fun demoteToMember() {
        role = CommunityRole.MEMBER
    }

    fun pullDomainEvents(): List<DomainEvent> {
        val events = domainEvents.toList()
        domainEvents.clear()
        return events
    }

    private fun registerEvent(event: DomainEvent) {
        domainEvents.add(event)
    }

    private fun transitTo(next: MembershipStatus) {
        if (!status.canTransitTo(next)) throw InvalidMembershipStateException(status, next)
        status = next
    }

    companion object {
        /** 일반 멤버 가입 — 공개 커뮤니티는 즉시 ACTIVE, 비공개는 PENDING_APPROVAL (FR-2). */
        fun join(communityId: Long, userId: Long, isPublic: Boolean): CommunityMember {
            if (isPublic) {
                return CommunityMember(
                    communityId = communityId,
                    userId = userId,
                    role = CommunityRole.MEMBER,
                    status = MembershipStatus.ACTIVE,
                    joinedAt = ZonedDateTime.now(),
                ).apply {
                    registerEvent(CommunityMemberJoinedEvent(memberId = id, communityId = communityId, userId = userId))
                }
            }
            return CommunityMember(
                communityId = communityId,
                userId = userId,
                role = CommunityRole.MEMBER,
                status = MembershipStatus.PENDING_APPROVAL,
                joinedAt = null,
            )
        }

        /** 커뮤니티 개설과 동시에 방장 멤버십을 생성한다. */
        fun createHost(communityId: Long, userId: Long): CommunityMember = CommunityMember(
            communityId = communityId,
            userId = userId,
            role = CommunityRole.HOST,
            status = MembershipStatus.ACTIVE,
            joinedAt = ZonedDateTime.now(),
        ).apply {
            registerEvent(CommunityMemberJoinedEvent(memberId = id, communityId = communityId, userId = userId))
        }

        /** 영속화 계층 복원 — 검증 없이 필드를 그대로 복구한다. */
        fun reconstitute(
            communityId: Long,
            userId: Long,
            role: CommunityRole,
            status: MembershipStatus,
            joinedAt: ZonedDateTime?,
        ): CommunityMember = CommunityMember(
            communityId = communityId,
            userId = userId,
            role = role,
            status = status,
            joinedAt = joinedAt,
        )
    }
}

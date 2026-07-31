package com.sportsapp.domain.community.entity

import com.sportsapp.domain.common.JpaAuditingBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 소모임↔예약 연결 애그리거트 (TDD B3, Detail Design "CommunityBooking").
 *
 * community가 booking의 Slot을 [slotId]로만 참조한다(R1 준수 — booking import 없이 ID 참조).
 * 표시용 시설·일시·정원은 소유하지 않고 [com.sportsapp.domain.community.gateway.SlotInfoGateway]가
 * 조회해 조합한다. 정원은 Slot이 소유하며 이 애그리거트는 링크 사실만 보유한다.
 */
@Entity
@Table(name = "community_bookings")
class CommunityBooking private constructor(
    @Column(name = "community_id", nullable = false)
    val communityId: Long,

    @Column(name = "slot_id", nullable = false)
    val slotId: Long,

    @Column(name = "linked_by_user_id", nullable = false)
    val linkedByUserId: Long,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    companion object {
        /** 신규 연결 생성 — 중복 링크 멱등 가드는 DomainService가 저장 전 조회로 처리한다. */
        fun create(communityId: Long, slotId: Long, linkedByUserId: Long): CommunityBooking {
            require(communityId > 0) { "communityId must be positive" }
            require(slotId > 0) { "slotId must be positive" }
            return CommunityBooking(
                communityId = communityId,
                slotId = slotId,
                linkedByUserId = linkedByUserId,
            )
        }

        /** 영속화 계층 복원 — 검증 없이 필드를 그대로 복구한다. */
        fun reconstitute(communityId: Long, slotId: Long, linkedByUserId: Long): CommunityBooking =
            CommunityBooking(
                communityId = communityId,
                slotId = slotId,
                linkedByUserId = linkedByUserId,
            )
    }
}

package com.sportsapp.domain.community.repository

import com.sportsapp.domain.community.entity.CommunityMember

/**
 * 커뮤니티 멤버십 영속화 계약 (TDD 인터페이스 시그니처 — 확정 3종 + 내부 조회용 1종 추가).
 *
 * - [findActiveBy]: TDD 확정 시그니처. **MembershipStatus.ACTIVE** 인 레코드만 반환한다
 *   (FR-13 ② `CommunityDomainService.requireActiveMember` 인가 가드 전용).
 * - [findBy]: 상태(PENDING_APPROVAL/ACTIVE/LEFT/KICKED)와 무관하게 communityId+userId 의
 *   현재 멤버십 레코드를 조회한다. approve/kick/leave/transfer 가 대상 레코드를 찾아
 *   그 위에 상태 전이 메서드를 호출하기 위해 필요하다(TDD 확정 목록에는 없으나, 상태 전이
 *   대상이 항상 ACTIVE 는 아니라서 — 예: approve 대상은 PENDING_APPROVAL — 추가했다).
 */
interface CommunityMemberRepository {
    fun save(member: CommunityMember): CommunityMember
    fun findBy(communityId: Long, userId: Long): CommunityMember?
    fun findActiveBy(communityId: Long, userId: Long): CommunityMember?
    fun findActiveByCommunityId(communityId: Long): List<CommunityMember>
}

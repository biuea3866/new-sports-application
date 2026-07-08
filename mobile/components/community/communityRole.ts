/**
 * communityRole — 뷰어(현재 사용자)의 커뮤니티 내 역할·멤버십 상태를 판정하는 순수 유틸.
 *
 * 근거: `20260704-채팅시스템고도화-tdd.md` "REST API 계약"(`GET /communities/{id}/members`는
 * ACTIVE 멤버만 반환) · design-fe-app.md S5 "권한별" 표.
 *
 * `GET /communities/{id}/members`는 ACTIVE 멤버만 반환하므로, 가입 신청 직후의
 * PENDING_APPROVAL 상태는 서버 재조회로 알 수 없다 — 컨테이너가 join 성공 응답의 status를
 * 세션 로컬 상태로 들고 있다가 `isPendingApproval`로 이 유틸에 전달한다.
 */
import type { CommunityMemberResponse } from '../../api/community-types';

export type ViewerMembership =
  | { kind: 'non-member' }
  | { kind: 'pending' }
  | { kind: 'member'; role: 'MEMBER' }
  | { kind: 'host' };

interface ResolveViewerMembershipParams {
  members: CommunityMemberResponse[];
  myUserId: number;
  isPendingApproval: boolean;
}

/** ACTIVE 멤버 목록에서 내 멤버십 레코드를 찾는다. 없으면 undefined. */
export function findMyMembership(
  members: CommunityMemberResponse[],
  myUserId: number
): CommunityMemberResponse | undefined {
  return members.find((candidate) => candidate.userId === myUserId);
}

/** 뷰어의 멤버십 상태를 판정한다. ACTIVE 목록 → 방장/멤버, 없으면 승인 대기/비멤버로 좁힌다. */
export function resolveViewerMembership({
  members,
  myUserId,
  isPendingApproval,
}: ResolveViewerMembershipParams): ViewerMembership {
  const myMembership = findMyMembership(members, myUserId);

  if (myMembership) {
    return myMembership.role === 'HOST' ? { kind: 'host' } : { kind: 'member', role: 'MEMBER' };
  }
  if (isPendingApproval) {
    return { kind: 'pending' };
  }
  return { kind: 'non-member' };
}

/** 방장만 멤버별 강퇴·위임 액션을 관리할 수 있다(FR-3). */
export function canManageMembers(viewer: ViewerMembership): boolean {
  return viewer.kind === 'host';
}

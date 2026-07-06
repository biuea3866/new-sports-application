/**
 * communityRole — 뷰어(현재 사용자)의 커뮤니티 내 역할·멤버십 상태 판정 유틸 단위 테스트.
 * 근거: FE-12 티켓 "테스트 케이스", design-fe-app.md S5 "권한별" 표.
 */
import type { CommunityMemberResponse } from '../../../api/community-types';
import { canManageMembers, findMyMembership, resolveViewerMembership } from '../communityRole';

function member(overrides: Partial<CommunityMemberResponse> = {}): CommunityMemberResponse {
  return {
    id: 1,
    communityId: 10,
    userId: 100,
    role: 'MEMBER',
    status: 'ACTIVE',
    joinedAt: '2026-07-01T00:00:00Z',
    ...overrides,
  };
}

describe('resolveViewerMembership', () => {
  it('ACTIVE 멤버 목록에서 내 role이 HOST면 host를 반환한다', () => {
    const members = [member({ userId: 1, role: 'HOST' })];

    expect(resolveViewerMembership({ members, myUserId: 1, isPendingApproval: false })).toEqual({
      kind: 'host',
    });
  });

  it('ACTIVE 멤버 목록에서 내 role이 MEMBER면 member를 반환한다', () => {
    const members = [member({ userId: 2, role: 'MEMBER' })];

    expect(resolveViewerMembership({ members, myUserId: 2, isPendingApproval: false })).toEqual({
      kind: 'member',
      role: 'MEMBER',
    });
  });

  it('목록에 내가 없고 승인 대기 플래그가 true면 pending을 반환한다', () => {
    const members = [member({ userId: 999, role: 'HOST' })];

    expect(resolveViewerMembership({ members, myUserId: 3, isPendingApproval: true })).toEqual({
      kind: 'pending',
    });
  });

  it('목록에 내가 없고 승인 대기 플래그가 false면 non-member를 반환한다', () => {
    const members = [member({ userId: 999, role: 'HOST' })];

    expect(resolveViewerMembership({ members, myUserId: 3, isPendingApproval: false })).toEqual({
      kind: 'non-member',
    });
  });

  it('목록이 비어 있어도(members 403 등) 판정이 가능하다', () => {
    expect(resolveViewerMembership({ members: [], myUserId: 1, isPendingApproval: false })).toEqual(
      { kind: 'non-member' }
    );
  });
});

describe('canManageMembers', () => {
  it('host만 true를 반환한다', () => {
    expect(canManageMembers({ kind: 'host' })).toBe(true);
    expect(canManageMembers({ kind: 'member', role: 'MEMBER' })).toBe(false);
    expect(canManageMembers({ kind: 'pending' })).toBe(false);
    expect(canManageMembers({ kind: 'non-member' })).toBe(false);
  });
});

describe('findMyMembership', () => {
  it('userId가 일치하는 멤버 레코드를 반환한다', () => {
    const target = member({ userId: 5 });

    expect(findMyMembership([member({ userId: 1 }), target], 5)).toBe(target);
  });

  it('일치하는 멤버가 없으면 undefined를 반환한다', () => {
    expect(findMyMembership([member({ userId: 1 })], 99)).toBeUndefined();
  });
});

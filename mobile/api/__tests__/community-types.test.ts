/**
 * community-types.ts 단위 테스트
 *
 * 타입 전용 모듈이라 런타임 로직은 없다. 아래 테스트는
 * ① 계약(TDD "응답 DTO 필드 스키마")과 일치하는 리터럴 객체가 각 인터페이스에
 *   그대로 대입 가능한지(컴파일 타임 계약 검증)와
 * ② 유니온 타입의 실제 허용 값 목록이 BE 계약과 일치하는지를 런타임에서 검증한다.
 */
import type {
  CommunityResponse,
  CommunityMemberResponse,
  CreateCommunityRequest,
  MembershipResponse,
  TransferHostRequest,
  CommunityVisibility,
  MemberRole,
  MembershipStatus,
} from '../community-types';

describe('CommunityResponse', () => {
  it('BE 계약 9개 필드(id~createdAt)를 그대로 만족한다', () => {
    const response: CommunityResponse = {
      id: 1,
      name: '주말 축구 모임',
      description: '동네에서 주말마다 축구해요',
      visibility: 'PUBLIC',
      sportCategory: 'SOCCER',
      hostUserId: 10,
      memberCount: 32,
      roomId: 100,
      createdAt: '2026-07-04T12:00:00+09:00',
    };

    expect(response.name).toBe('주말 축구 모임');
    expect(response.memberCount).toBe(32);
  });

  it('description·roomId는 null을 허용한다', () => {
    const response: CommunityResponse = {
      id: 2,
      name: '농구 동아리',
      description: null,
      visibility: 'PRIVATE',
      sportCategory: 'BASKETBALL',
      hostUserId: 11,
      memberCount: 1,
      roomId: null,
      createdAt: '2026-07-04T12:00:00+09:00',
    };

    expect(response.description).toBeNull();
    expect(response.roomId).toBeNull();
  });
});

describe('CreateCommunityRequest', () => {
  it('POST /communities 계약 필드(name,description,visibility,sportCategory)와 일치한다', () => {
    const request: CreateCommunityRequest = {
      name: '주말 축구 모임',
      description: '동네에서 주말마다 축구해요',
      visibility: 'PUBLIC',
      sportCategory: 'SOCCER',
    };

    expect(Object.keys(request).sort()).toEqual(
      ['description', 'name', 'sportCategory', 'visibility'].sort()
    );
  });

  it('description을 생략해도 유효하다', () => {
    const request: CreateCommunityRequest = {
      name: '농구 동아리',
      visibility: 'PRIVATE',
      sportCategory: 'BASKETBALL',
    };

    expect(request.description).toBeUndefined();
  });
});

describe('CommunityVisibility · MemberRole 유니온', () => {
  it('CommunityVisibility는 PUBLIC|PRIVATE만 허용한다', () => {
    const values: CommunityVisibility[] = ['PUBLIC', 'PRIVATE'];
    expect(values).toEqual(['PUBLIC', 'PRIVATE']);
  });

  it('MemberRole은 HOST|MEMBER만 허용한다 (BE CommunityRole과 일치)', () => {
    const values: MemberRole[] = ['HOST', 'MEMBER'];
    expect(values).toEqual(['HOST', 'MEMBER']);
  });
});

describe('CommunityMemberResponse', () => {
  it('BE 계약 6개 필드를 만족하고 joinedAt은 ACTIVE 전이 시각을 담는다', () => {
    const member: CommunityMemberResponse = {
      id: 1,
      communityId: 2,
      userId: 10,
      role: 'HOST',
      status: 'ACTIVE',
      joinedAt: '2026-07-04T12:00:00+09:00',
    };

    expect(member.status).toBe('ACTIVE');
    expect(member.joinedAt).not.toBeNull();
  });

  it('PENDING_APPROVAL 상태는 joinedAt이 null이다', () => {
    const member: CommunityMemberResponse = {
      id: 2,
      communityId: 2,
      userId: 11,
      role: 'MEMBER',
      status: 'PENDING_APPROVAL',
      joinedAt: null,
    };

    expect(member.joinedAt).toBeNull();
  });

  it('MembershipStatus는 ACTIVE|PENDING_APPROVAL|LEFT|KICKED 4개 값을 허용한다', () => {
    const values: MembershipStatus[] = ['ACTIVE', 'PENDING_APPROVAL', 'LEFT', 'KICKED'];
    expect(values).toEqual(['ACTIVE', 'PENDING_APPROVAL', 'LEFT', 'KICKED']);
  });
});

describe('MembershipResponse', () => {
  it('join 응답 상태로 ACTIVE를 포함한다', () => {
    const response: MembershipResponse = { status: 'ACTIVE' };
    expect(response.status).toBe('ACTIVE');
  });

  it('join 응답 상태로 PENDING_APPROVAL을 포함한다', () => {
    const response: MembershipResponse = { status: 'PENDING_APPROVAL' };
    expect(response.status).toBe('PENDING_APPROVAL');
  });
});

describe('TransferHostRequest', () => {
  it('POST /communities/{id}/host/transfer 계약 필드 newHostUserId와 일치한다', () => {
    const request: TransferHostRequest = { newHostUserId: 99 };
    expect(request).toEqual({ newHostUserId: 99 });
  });
});

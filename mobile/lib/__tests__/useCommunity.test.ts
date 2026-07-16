/**
 * useCommunities가 목록을 반환하고 keyword로 필터 쿼리를 보낸다
 * useJoinCommunity 공개 커뮤니티는 status ACTIVE, 비공개는 PENDING_APPROVAL을 반환한다
 * useCreateCommunity 성공 시 방목록 캐시가 무효화된다(전용방 자동생성)
 * useLeaveCommunity 성공 시 방목록 캐시가 무효화된다(자동 퇴장)
 * useKickMember가 방장 권한 없을 때 403을 전파한다
 */
import { createElement } from 'react';
import { act } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react-native';

import {
  useApproveMember,
  useCommunities,
  useCommunity,
  useCommunityMembers,
  useCreateCommunity,
  useJoinCommunity,
  useKickMember,
  useLeaveCommunity,
  useTransferHost,
} from '../useCommunity';
import { MY_ROOMS_QUERY_KEY } from '../useRooms';
import type {
  CommunityMemberResponse,
  CommunityResponse,
  MembershipResponse,
} from '../../api/community-types';

jest.mock('../../api/community', () => ({
  listCommunities: jest.fn(),
  getCommunity: jest.fn(),
  listMembers: jest.fn(),
  createCommunity: jest.fn(),
  joinCommunity: jest.fn(),
  approveMember: jest.fn(),
  kickMember: jest.fn(),
  transferHost: jest.fn(),
  leaveCommunity: jest.fn(),
}));

import {
  approveMember,
  createCommunity,
  getCommunity,
  joinCommunity,
  kickMember,
  leaveCommunity,
  listCommunities,
  listMembers,
  transferHost,
} from '../../api/community';

const listCommunitiesMock = listCommunities as jest.MockedFunction<typeof listCommunities>;
const getCommunityMock = getCommunity as jest.MockedFunction<typeof getCommunity>;
const listMembersMock = listMembers as jest.MockedFunction<typeof listMembers>;
const createCommunityMock = createCommunity as jest.MockedFunction<typeof createCommunity>;
const joinCommunityMock = joinCommunity as jest.MockedFunction<typeof joinCommunity>;
const approveMemberMock = approveMember as jest.MockedFunction<typeof approveMember>;
const kickMemberMock = kickMember as jest.MockedFunction<typeof kickMember>;
const transferHostMock = transferHost as jest.MockedFunction<typeof transferHost>;
const leaveCommunityMock = leaveCommunity as jest.MockedFunction<typeof leaveCommunity>;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  return { wrapper, queryClient };
}

const soccerCommunity: CommunityResponse = {
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

const basketballCommunity: CommunityResponse = {
  ...soccerCommunity,
  id: 2,
  name: '농구 동아리',
  visibility: 'PRIVATE',
  sportCategory: 'BASKETBALL',
};

describe('useCommunities', () => {
  afterEach(() => jest.clearAllMocks());

  it('목록을 반환하고 keyword로 필터 쿼리를 보낸다', async () => {
    listCommunitiesMock.mockResolvedValue([soccerCommunity]);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useCommunities('축구'), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual([soccerCommunity]);
    expect(listCommunitiesMock).toHaveBeenCalledWith('축구');
  });

  it('keyword 없이 호출하면 전체 목록 쿼리를 보낸다', async () => {
    listCommunitiesMock.mockResolvedValue([soccerCommunity, basketballCommunity]);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useCommunities(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toHaveLength(2);
    expect(listCommunitiesMock).toHaveBeenCalledWith(undefined);
  });
});

describe('useCommunity · useCommunityMembers', () => {
  afterEach(() => jest.clearAllMocks());

  it('useCommunity는 id로 커뮤니티 상세를 반환한다', async () => {
    getCommunityMock.mockResolvedValue(soccerCommunity);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useCommunity(1), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(soccerCommunity);
    expect(getCommunityMock).toHaveBeenCalledWith(1);
  });

  it('useCommunityMembers는 id로 활성 멤버 목록을 반환한다', async () => {
    const activeMember: CommunityMemberResponse = {
      id: 1,
      communityId: 1,
      userId: 10,
      role: 'HOST',
      status: 'ACTIVE',
      joinedAt: '2026-07-04T12:00:00+09:00',
    };
    listMembersMock.mockResolvedValue([activeMember]);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useCommunityMembers(1), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([activeMember]);
    expect(listMembersMock).toHaveBeenCalledWith(1);
  });

  /**
   * [버그3] 실제 앱에서 쓰는 queryClient(retry: shouldRetryQuery)를 그대로 사용해,
   * 비ACTIVE 멤버의 403 응답이 재시도 없이 즉시 실패 처리되는지 검증한다.
   * 재시도가 남아 있으면 화면이 "멤버 접근 제한" 안내가 뜨기 전에 깜빡인다.
   */
  it('403 응답은 재시도 없이 즉시 실패 처리된다(실제 queryClient 재시도 정책)', async () => {
    const forbiddenError = Object.assign(new Error('Forbidden'), {
      isAxiosError: true,
      response: { status: 403 },
    });
    listMembersMock.mockRejectedValue(forbiddenError);
    const { queryClient: appQueryClient } = jest.requireActual('../query-client');
    const wrapper = ({ children }: { children: React.ReactNode }) =>
      createElement(QueryClientProvider, { client: appQueryClient }, children);

    const { result } = renderHook(() => useCommunityMembers(1), { wrapper });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(listMembersMock).toHaveBeenCalledTimes(1);

    appQueryClient.clear();
  });
});

describe('useJoinCommunity', () => {
  afterEach(() => jest.clearAllMocks());

  it('공개 커뮤니티는 status ACTIVE를 반환한다', async () => {
    const activeMembership: MembershipResponse = { status: 'ACTIVE' };
    joinCommunityMock.mockResolvedValue(activeMembership);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useJoinCommunity(1), { wrapper });

    await act(async () => {
      await result.current.mutateAsync();
    });

    await waitFor(() => expect(result.current.data).toEqual({ status: 'ACTIVE' }));
    expect(joinCommunityMock).toHaveBeenCalledWith(1);
  });

  it('비공개 커뮤니티는 status PENDING_APPROVAL을 반환한다', async () => {
    const pendingMembership: MembershipResponse = { status: 'PENDING_APPROVAL' };
    joinCommunityMock.mockResolvedValue(pendingMembership);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useJoinCommunity(2), { wrapper });

    await act(async () => {
      await result.current.mutateAsync();
    });

    await waitFor(() => expect(result.current.data).toEqual({ status: 'PENDING_APPROVAL' }));
    expect(joinCommunityMock).toHaveBeenCalledWith(2);
  });
});

describe('useCreateCommunity', () => {
  afterEach(() => jest.clearAllMocks());

  it('성공 시 방목록·커뮤니티목록 캐시가 무효화된다(전용방 자동생성)', async () => {
    createCommunityMock.mockResolvedValue(soccerCommunity);
    const { wrapper, queryClient } = createWrapper();
    const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useCreateCommunity(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({
        name: '주말 축구 모임',
        visibility: 'PUBLIC',
        sportCategory: 'SOCCER',
      });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: MY_ROOMS_QUERY_KEY });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['communities'] });
  });
});

describe('useLeaveCommunity', () => {
  afterEach(() => jest.clearAllMocks());

  it('성공 시 방목록 캐시가 무효화된다(자동 퇴장)', async () => {
    leaveCommunityMock.mockResolvedValue(undefined);
    const { wrapper, queryClient } = createWrapper();
    const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useLeaveCommunity(1), { wrapper });

    await act(async () => {
      await result.current.mutateAsync();
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(leaveCommunityMock).toHaveBeenCalledWith(1);
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: MY_ROOMS_QUERY_KEY });
  });
});

describe('useKickMember', () => {
  afterEach(() => jest.clearAllMocks());

  it('방장 권한 없을 때 403을 전파한다', async () => {
    const forbiddenError = Object.assign(new Error('Forbidden'), { status: 403 });
    kickMemberMock.mockRejectedValue(forbiddenError);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useKickMember(1), { wrapper });

    await act(async () => {
      await expect(result.current.mutateAsync({ userId: 99 })).rejects.toThrow('Forbidden');
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect((result.current.error as { status?: number } | null)?.status).toBe(403);
  });
});

describe('useApproveMember · useTransferHost — 나머지 mutation 훅 동작', () => {
  afterEach(() => jest.clearAllMocks());

  it('useApproveMember는 communityId·userId로 승인 요청을 보낸다', async () => {
    const approvedMember: CommunityMemberResponse = {
      id: 2,
      communityId: 2,
      userId: 11,
      role: 'MEMBER',
      status: 'ACTIVE',
      joinedAt: '2026-07-04T12:00:00+09:00',
    };
    approveMemberMock.mockResolvedValue(approvedMember);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useApproveMember(2), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({ userId: 11 });
    });

    await waitFor(() => expect(result.current.data).toEqual(approvedMember));
    expect(approveMemberMock).toHaveBeenCalledWith(2, 11);
  });

  it('useTransferHost는 communityId·newHostUserId로 위임 요청을 보낸다', async () => {
    transferHostMock.mockResolvedValue(undefined);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useTransferHost(1), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({ newHostUserId: 20 });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(transferHostMock).toHaveBeenCalledWith(1, 20);
  });
});

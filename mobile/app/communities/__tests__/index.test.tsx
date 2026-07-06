/**
 * CommunitiesListScreen(S3, FE-11) — 목록 렌더·상태·탭 이동 검증.
 * 근거: FE-11 티켓 테스트 케이스, design-fe-app.md S3.
 *
 * useCommunities를 모킹해 화면 배선(카드 표시·4상태·이동)만 사용자 관점으로 검증한다.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { CommunityResponse } from '../../../api/community-types';
import CommunitiesListScreen from '../index';

jest.mock('../../../lib/useCommunity', () => ({
  useCommunities: jest.fn(),
}));

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), replace: jest.fn() },
}));

import { router } from 'expo-router';
import { useCommunities } from '../../../lib/useCommunity';

const useCommunitiesMock = useCommunities as jest.MockedFunction<typeof useCommunities>;

const PUBLIC_COMMUNITY: CommunityResponse = {
  id: 1,
  name: '주말 축구 모임',
  description: '동네 축구 같이 해요',
  visibility: 'PUBLIC',
  sportCategory: 'SOCCER',
  hostUserId: 10,
  memberCount: 32,
  roomId: 100,
  createdAt: '2026-07-01T00:00:00+09:00',
};

const PRIVATE_COMMUNITY: CommunityResponse = {
  id: 2,
  name: '새벽 농구',
  description: null,
  visibility: 'PRIVATE',
  sportCategory: 'BASKETBALL',
  hostUserId: 11,
  memberCount: 8,
  roomId: 101,
  createdAt: '2026-07-02T00:00:00+09:00',
};

function mockCommunities(overrides: Partial<ReturnType<typeof useCommunities>>) {
  useCommunitiesMock.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useCommunities>);
}

describe('CommunitiesListScreen', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('커뮤니티 목록이 카드로 렌더되고 공개/비공개 표시가 정확하다', () => {
    mockCommunities({ data: [PUBLIC_COMMUNITY, PRIVATE_COMMUNITY] });

    render(<CommunitiesListScreen />);

    expect(screen.getByText('⚽ 주말 축구 모임')).toBeTruthy();
    expect(screen.getByText('공개 · 32명')).toBeTruthy();
    expect(screen.getByText('🏀 새벽 농구')).toBeTruthy();
    expect(screen.getByText('비공개 승인제 · 8명')).toBeTruthy();
  });

  it('목록이 비면 빈 상태와 개설 유도가 렌더된다', () => {
    mockCommunities({ data: [] });

    render(<CommunitiesListScreen />);

    expect(screen.getByText('아직 동아리가 없어요. 첫 동아리를 만들어보세요')).toBeTruthy();
    expect(screen.getByLabelText('동아리 개설')).toBeTruthy();
  });

  it('로딩 중이면 스켈레톤이 렌더된다', () => {
    mockCommunities({ isLoading: true });

    render(<CommunitiesListScreen />);

    expect(screen.getByLabelText('로딩 중')).toBeTruthy();
  });

  it('오류 시 ErrorView가 렌더되고 재시도를 탭하면 refetch가 호출된다', () => {
    const refetchMock = jest.fn();
    mockCommunities({ isError: true, refetch: refetchMock });

    render(<CommunitiesListScreen />);
    fireEvent.press(screen.getByLabelText('다시 시도'));

    expect(refetchMock).toHaveBeenCalled();
  });

  it('카드를 탭하면 상세 화면으로 이동한다', () => {
    mockCommunities({ data: [PUBLIC_COMMUNITY] });

    render(<CommunitiesListScreen />);
    fireEvent.press(screen.getByLabelText('주말 축구 모임, 공개, 멤버 32명'));

    expect(router.push).toHaveBeenCalledWith('/communities/1');
  });

  it('플로팅 개설 CTA를 탭하면 개설 화면으로 이동한다', () => {
    mockCommunities({ data: [PUBLIC_COMMUNITY] });

    render(<CommunitiesListScreen />);
    fireEvent.press(screen.getByLabelText('동아리 개설'));

    expect(router.push).toHaveBeenCalledWith('/communities/new');
  });

  it('다크 모드에서도 카드가 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    mockCommunities({ data: [PUBLIC_COMMUNITY] });

    render(<CommunitiesListScreen />);

    expect(screen.getByText('⚽ 주말 축구 모임')).toBeTruthy();
  });
});

/**
 * CommunityTabScreen — 전역 게시글 목록·종목 필터(A-P5) + 동아리 세그먼트 통합·
 * 채팅 진입 아이콘 사용자 관점 동작 검증.
 * 근거: design-fe-app.md Testing Plan "종목 필터", 사용자 피드백
 * "커뮤니티 = 기존 커뮤니티(게시글) + 동아리를 세그먼트 컨트롤로 통합" +
 * "채팅은 탭에서 제거 → 홈·커뮤니티 화면 상단 우측 아이콘으로 진입".
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { PageResponse, PostResponse } from '../../../api/types';
import type { CommunityResponse } from '../../../api/community-types';
import CommunityTabScreen from '../community';

jest.mock('../../../lib/usePosts', () => ({
  usePosts: jest.fn(),
}));

jest.mock('../../../lib/useCommunity', () => ({
  useCommunities: jest.fn(),
}));

jest.mock('../../../lib/useTotalUnread', () => ({
  useTotalUnread: jest.fn(),
}));

jest.mock('../../../lib/feature-flags', () => ({
  isFeatureEnabled: jest.fn(),
}));

jest.mock('expo-router', () => ({
  useRouter: jest.fn(),
}));

import { useRouter } from 'expo-router';
import { isFeatureEnabled } from '../../../lib/feature-flags';
import { usePosts } from '../../../lib/usePosts';
import { useCommunities } from '../../../lib/useCommunity';
import { useTotalUnread } from '../../../lib/useTotalUnread';

const usePostsMock = usePosts as jest.MockedFunction<typeof usePosts>;
const useCommunitiesMock = useCommunities as jest.MockedFunction<typeof useCommunities>;
const useTotalUnreadMock = useTotalUnread as jest.MockedFunction<typeof useTotalUnread>;
const isFeatureEnabledMock = isFeatureEnabled as jest.MockedFunction<typeof isFeatureEnabled>;
const useRouterMock = useRouter as jest.MockedFunction<typeof useRouter>;

function page(content: PostResponse[]): PageResponse<PostResponse> {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 20 };
}

const POST: PostResponse = {
  id: 1,
  userId: 42,
  title: '주말 러닝 모임 후기',
  type: 'FREE',
  createdAt: '2026-07-06T00:00:00Z',
  communityId: null,
  sportCategory: 'RUNNING',
};

const COMMUNITY: CommunityResponse = {
  id: 5,
  name: '주말 러너스',
  description: '매주 토요일 러닝',
  visibility: 'PUBLIC',
  sportCategory: 'RUNNING',
  hostUserId: 1,
  memberCount: 12,
  roomId: 99,
  createdAt: '2026-07-01T00:00:00Z',
};

function mockPosts(overrides: Partial<ReturnType<typeof usePosts>> = {}) {
  usePostsMock.mockReturnValue({
    data: page([]),
    isLoading: false,
    isError: false,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof usePosts>);
}

function mockCommunities(overrides: Partial<ReturnType<typeof useCommunities>> = {}) {
  useCommunitiesMock.mockReturnValue({
    data: [],
    isLoading: false,
    isError: false,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useCommunities>);
}

describe('커뮤니티 탭 화면', () => {
  const pushMock = jest.fn();

  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useRouterMock.mockReturnValue({ push: pushMock } as unknown as ReturnType<typeof useRouter>);
    isFeatureEnabledMock.mockReturnValue(true);
    useTotalUnreadMock.mockReturnValue(0);
    mockPosts();
    mockCommunities();
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('게시글 목록을 카드로 렌더한다', () => {
    mockPosts({ data: page([POST]) });

    render(<CommunityTabScreen />);

    expect(screen.getByText('주말 러닝 모임 후기')).toBeTruthy();
  });

  it('목록이 비면 빈 상태를 표시한다', () => {
    mockPosts({ data: page([]) });

    render(<CommunityTabScreen />);

    expect(screen.getByText('게시글이 없어요')).toBeTruthy();
  });

  it('조회 실패 시 에러 뷰가 표시되고 재시도할 수 있다', () => {
    const refetch = jest.fn();
    mockPosts({ isError: true, refetch });

    render(<CommunityTabScreen />);
    fireEvent.press(screen.getByLabelText('다시 시도'));

    expect(refetch).toHaveBeenCalled();
  });

  it('community.post.enabled가 OFF면 종목 필터 칩이 렌더되지 않는다', () => {
    isFeatureEnabledMock.mockReturnValue(false);

    render(<CommunityTabScreen />);

    expect(screen.queryByLabelText('종목 선택')).toBeNull();
  });

  it('종목 칩을 선택하면 usePosts가 sportCategory 파라미터로 재호출된다', () => {
    render(<CommunityTabScreen />);
    fireEvent.press(screen.getByLabelText('🏃 러닝'));

    expect(usePostsMock).toHaveBeenLastCalledWith(0, 20, { sportCategory: 'RUNNING' });
  });

  it('선택한 종목에 게시글이 없으면 종목 전용 빈 문구를 표시한다', () => {
    render(<CommunityTabScreen />);
    fireEvent.press(screen.getByLabelText('🏃 러닝'));

    expect(screen.getByText('이 종목 글이 아직 없어요')).toBeTruthy();
  });

  it('게시글을 탭하면 게시글 상세로 이동한다', () => {
    mockPosts({ data: page([POST]) });

    render(<CommunityTabScreen />);
    fireEvent.press(screen.getByLabelText(/주말 러닝 모임 후기/));

    expect(pushMock).toHaveBeenCalledWith('/community/1');
  });

  it('게시글 작성 버튼을 탭하면 작성 화면으로 이동한다', () => {
    render(<CommunityTabScreen />);
    fireEvent.press(screen.getByLabelText('게시글 작성'));

    expect(pushMock).toHaveBeenCalledWith('/community/new');
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    mockPosts({ data: page([POST]) });

    render(<CommunityTabScreen />);

    expect(screen.getByText('주말 러닝 모임 후기')).toBeTruthy();
  });

  it('채팅 진입 아이콘이 상단에 렌더되고 탭하면 채팅방 목록으로 이동한다', () => {
    render(<CommunityTabScreen />);
    fireEvent.press(screen.getByLabelText('채팅'));

    expect(pushMock).toHaveBeenCalledWith('/rooms');
  });

  describe('동아리 세그먼트 (chat.community.enabled)', () => {
    it('chat.community.enabled가 OFF면 세그먼트 컨트롤이 보이지 않고 게시글만 표시된다', () => {
      isFeatureEnabledMock.mockImplementation((flag: string) => flag !== 'chat.community.enabled');
      mockPosts({ data: page([POST]) });

      render(<CommunityTabScreen />);

      expect(screen.queryByRole('button', { name: '동아리' })).toBeNull();
      expect(screen.getByText('주말 러닝 모임 후기')).toBeTruthy();
    });

    it('chat.community.enabled가 ON이면 게시글|동아리 세그먼트 컨트롤이 보인다', () => {
      render(<CommunityTabScreen />);

      expect(screen.getByRole('button', { name: '게시글' })).toBeTruthy();
      expect(screen.getByRole('button', { name: '동아리' })).toBeTruthy();
    });

    it('동아리 세그먼트를 탭하면 동아리 목록으로 전환된다', () => {
      mockPosts({ data: page([POST]) });
      mockCommunities({ data: [COMMUNITY] });

      render(<CommunityTabScreen />);
      fireEvent.press(screen.getByRole('button', { name: '동아리' }));

      expect(screen.getByText(/주말 러너스/)).toBeTruthy();
      expect(screen.queryByText('주말 러닝 모임 후기')).toBeNull();
    });

    it('동아리 카드를 탭하면 동아리 상세로 이동한다', () => {
      mockCommunities({ data: [COMMUNITY] });

      render(<CommunityTabScreen />);
      fireEvent.press(screen.getByRole('button', { name: '동아리' }));
      fireEvent.press(screen.getByTestId('community-card-5'));

      expect(pushMock).toHaveBeenCalledWith('/communities/5');
    });

    it('동아리 개설 버튼을 탭하면 개설 화면으로 이동한다', () => {
      render(<CommunityTabScreen />);
      fireEvent.press(screen.getByRole('button', { name: '동아리' }));
      fireEvent.press(screen.getByLabelText('동아리 개설'));

      expect(pushMock).toHaveBeenCalledWith('/communities/new');
    });

    it('동아리 목록이 비어있으면 빈 상태를 표시한다', () => {
      mockCommunities({ data: [] });

      render(<CommunityTabScreen />);
      fireEvent.press(screen.getByRole('button', { name: '동아리' }));

      expect(screen.getByText('아직 동아리가 없어요. 첫 동아리를 만들어보세요')).toBeTruthy();
    });

    it('동아리 조회 실패 시 에러 뷰와 재시도 버튼을 표시한다', () => {
      const refetch = jest.fn();
      mockCommunities({ isError: true, refetch });

      render(<CommunityTabScreen />);
      fireEvent.press(screen.getByRole('button', { name: '동아리' }));
      fireEvent.press(screen.getByLabelText('다시 시도'));

      expect(refetch).toHaveBeenCalled();
    });
  });
});

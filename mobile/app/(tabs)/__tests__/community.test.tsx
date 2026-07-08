/**
 * CommunityTabScreen(A-P5) — 전역 게시글 목록·종목 필터 사용자 관점 동작 검증.
 * 근거: design-fe-app.md Testing Plan "종목 필터".
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { PageResponse, PostResponse } from '../../../api/types';
import CommunityTabScreen from '../community';

jest.mock('../../../lib/usePosts', () => ({
  usePosts: jest.fn(),
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

const usePostsMock = usePosts as jest.MockedFunction<typeof usePosts>;
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

function mockPosts(overrides: Partial<ReturnType<typeof usePosts>> = {}) {
  usePostsMock.mockReturnValue({
    data: page([]),
    isLoading: false,
    isError: false,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof usePosts>);
}

describe('CommunityTabScreen(A-P5)', () => {
  const pushMock = jest.fn();

  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useRouterMock.mockReturnValue({ push: pushMock } as unknown as ReturnType<typeof useRouter>);
    isFeatureEnabledMock.mockReturnValue(true);
    mockPosts();
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
});

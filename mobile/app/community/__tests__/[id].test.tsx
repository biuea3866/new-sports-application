/**
 * CommunityDetailScreen(A-P4, 전역·모임 게시글 공용 상세) — 본문·댓글 4상태 사용자 관점 동작 검증.
 * 근거: design-fe-app.md Testing Plan "게시글 상세+댓글".
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import { AxiosError } from 'axios';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { CommentPageResponse, PostDetailResponse } from '../../../api/types';
import CommunityDetailScreen from '../[id]';

jest.mock('../../../lib/usePosts', () => ({
  usePost: jest.fn(),
  useComments: jest.fn(),
  useAddComment: jest.fn(),
}));

jest.mock('expo-router', () => ({
  useLocalSearchParams: jest.fn(),
}));

import { useLocalSearchParams } from 'expo-router';
import { useAddComment, useComments, usePost } from '../../../lib/usePosts';

const usePostMock = usePost as jest.MockedFunction<typeof usePost>;
const useCommentsMock = useComments as jest.MockedFunction<typeof useComments>;
const useAddCommentMock = useAddComment as jest.MockedFunction<typeof useAddComment>;
const useLocalSearchParamsMock = useLocalSearchParams as jest.MockedFunction<
  typeof useLocalSearchParams
>;

function axiosErrorWithStatus(status: number): AxiosError {
  return new AxiosError('boom', undefined, undefined, undefined, {
    status,
    data: {},
    statusText: '',
    headers: {},
    config: {} as never,
  });
}

const POST: PostDetailResponse = {
  id: 1,
  userId: 42,
  title: '토요일 경기 후기',
  content: '오늘 경기 재밌었어요',
  type: 'FREE',
  createdAt: '2026-07-06T00:00:00Z',
  comments: [],
  communityId: 5,
  sportCategory: 'SOCCER',
};

function commentPage(content: CommentPageResponse['content']): CommentPageResponse {
  return { content, totalElements: content.length, totalPages: 1, page: 0, size: 20 };
}

function mockPost(overrides: Partial<ReturnType<typeof usePost>> = {}) {
  usePostMock.mockReturnValue({
    data: POST,
    isLoading: false,
    isError: false,
    error: null,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof usePost>);
}

function mockComments(overrides: Partial<ReturnType<typeof useComments>> = {}) {
  useCommentsMock.mockReturnValue({
    data: commentPage([]),
    isLoading: false,
    isError: false,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useComments>);
}

function mockAddComment(overrides: Record<string, unknown> = {}) {
  useAddCommentMock.mockReturnValue({
    mutate: jest.fn(),
    isPending: false,
    ...overrides,
  } as unknown as ReturnType<typeof useAddComment>);
}

describe('커뮤니티 게시글 상세 화면', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useLocalSearchParamsMock.mockReturnValue({ id: '1' });
    mockPost();
    mockComments();
    mockAddComment();
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('본문·작성자·댓글 0건 안내를 렌더한다', () => {
    render(<CommunityDetailScreen />);

    expect(screen.getByText('토요일 경기 후기')).toBeTruthy();
    expect(screen.getByText('오늘 경기 재밌었어요')).toBeTruthy();
    expect(screen.getByText('첫 댓글을 남겨보세요')).toBeTruthy();
  });

  it('로딩 중이면 로딩 뷰를 렌더한다', () => {
    mockPost({ isLoading: true, data: undefined });

    render(<CommunityDetailScreen />);

    expect(screen.getByLabelText('로딩 중')).toBeTruthy();
  });

  it('NOTICE 타입이면 공지 배지가 표시된다', () => {
    mockPost({ data: { ...POST, type: 'NOTICE' } });

    render(<CommunityDetailScreen />);

    expect(screen.getByText('📌 공지')).toBeTruthy();
  });

  it('403이면 잠금 상태 안내를 렌더한다', () => {
    mockPost({ data: undefined, isError: true, error: axiosErrorWithStatus(403) });

    render(<CommunityDetailScreen />);

    expect(screen.getByText('🔒 멤버만 볼 수 있어요')).toBeTruthy();
  });

  it('404면 없는 게시글 안내를 렌더한다', () => {
    mockPost({ data: undefined, isError: true, error: axiosErrorWithStatus(404) });

    render(<CommunityDetailScreen />);

    expect(screen.getByText('없는 게시글이에요')).toBeTruthy();
  });

  it('그 외 오류는 재시도 가능한 에러 뷰를 렌더한다', () => {
    const refetch = jest.fn();
    mockPost({ data: undefined, isError: true, error: new Error('boom'), refetch });

    render(<CommunityDetailScreen />);
    fireEvent.press(screen.getByLabelText('다시 시도'));

    expect(refetch).toHaveBeenCalled();
  });

  it('댓글이 있으면 목록으로 렌더된다', () => {
    mockComments({
      data: commentPage([
        { id: 1, postId: 1, userId: 10, content: '좋아요!', createdAt: '2026-07-07T00:00:00Z' },
      ]),
    });

    render(<CommunityDetailScreen />);

    expect(screen.getByText('좋아요!')).toBeTruthy();
  });

  it('댓글을 입력하고 등록하면 mutate가 호출되고 입력창이 비워진다', () => {
    const mutate = jest.fn((_content: string, options?: { onSuccess?: () => void }) =>
      options?.onSuccess?.()
    );
    mockAddComment({ mutate });

    render(<CommunityDetailScreen />);
    fireEvent.changeText(screen.getByLabelText('댓글 입력'), '저도 재밌었어요');
    fireEvent.press(screen.getByLabelText('등록'));

    expect(mutate).toHaveBeenCalledWith('저도 재밌었어요', expect.anything());
    expect(screen.getByLabelText('댓글 입력').props.value).toBe('');
  });

  it('댓글 입력이 비어 있으면 등록 버튼이 비활성 상태다', () => {
    render(<CommunityDetailScreen />);

    expect(screen.getByLabelText('등록').props.accessibilityState.disabled).toBe(true);
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<CommunityDetailScreen />);

    expect(screen.getByText('토요일 경기 후기')).toBeTruthy();
  });
});

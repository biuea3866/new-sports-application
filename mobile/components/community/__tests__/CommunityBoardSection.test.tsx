/**
 * CommunityBoardSection(A-P1/A-P2) — 게시판 섹션 4상태·글쓰기 CTA 게이팅 검증.
 * 근거: design-fe-app.md Testing Plan "게시판 섹션".
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import { AxiosError } from 'axios';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { PageResponse, PostResponse } from '../../../api/types';
import { CommunityBoardSection } from '../CommunityBoardSection';

jest.mock('../../../lib/usePosts', () => ({
  useCommunityPosts: jest.fn(),
}));

import { useCommunityPosts } from '../../../lib/usePosts';

const useCommunityPostsMock = useCommunityPosts as jest.MockedFunction<typeof useCommunityPosts>;

function forbiddenError(): AxiosError {
  return new AxiosError('Forbidden', undefined, undefined, undefined, {
    status: 403,
    data: {},
    statusText: 'Forbidden',
    headers: {},
    config: {} as never,
  });
}

function page(content: PostResponse[]): PageResponse<PostResponse> {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 20 };
}

const NOTICE_POST: PostResponse = {
  id: 1,
  userId: 10,
  title: '이번 달 회비 안내',
  type: 'NOTICE',
  createdAt: '2026-07-06T00:00:00Z',
  communityId: 5,
  sportCategory: 'SOCCER',
};

function mockPosts(overrides: Partial<ReturnType<typeof useCommunityPosts>>) {
  useCommunityPostsMock.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    error: null,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useCommunityPosts>);
}

describe('CommunityBoardSection', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('ACTIVE 멤버에게는 글쓰기 CTA가 노출된다', () => {
    mockPosts({ data: page([]) });

    render(
      <CommunityBoardSection
        communityId={5}
        canWrite
        onCreatePost={jest.fn()}
        onPostPress={jest.fn()}
      />
    );

    expect(screen.getByLabelText('+ 글쓰기')).toBeTruthy();
  });

  it('비멤버에게는 글쓰기 CTA가 숨겨진다', () => {
    mockPosts({ data: page([]) });

    render(
      <CommunityBoardSection
        communityId={5}
        canWrite={false}
        onCreatePost={jest.fn()}
        onPostPress={jest.fn()}
      />
    );

    expect(screen.queryByLabelText('+ 글쓰기')).toBeNull();
  });

  it('게시글 0건이면 빈 상태를 표시한다', () => {
    mockPosts({ data: page([]) });

    render(
      <CommunityBoardSection
        communityId={5}
        canWrite={false}
        onCreatePost={jest.fn()}
        onPostPress={jest.fn()}
      />
    );

    expect(screen.getByText('첫 글을 남겨보세요')).toBeTruthy();
  });

  it('NOTICE 게시글은 상단에 공지 배지로 표시된다', () => {
    mockPosts({ data: page([NOTICE_POST]) });

    render(
      <CommunityBoardSection
        communityId={5}
        canWrite={false}
        onCreatePost={jest.fn()}
        onPostPress={jest.fn()}
      />
    );

    expect(screen.getByText('📌 공지')).toBeTruthy();
  });

  it('PRIVATE 비멤버(403)는 잠금 상태 안내를 본다', () => {
    mockPosts({ data: undefined, isError: true, error: forbiddenError() });

    render(
      <CommunityBoardSection
        communityId={5}
        canWrite={false}
        onCreatePost={jest.fn()}
        onPostPress={jest.fn()}
      />
    );

    expect(screen.getByText('🔒 멤버만 볼 수 있어요')).toBeTruthy();
  });

  it('403이 아닌 오류는 재시도 가능한 에러 뷰로 표시된다', () => {
    const refetch = jest.fn();
    mockPosts({ data: undefined, isError: true, error: new Error('boom'), refetch });

    render(
      <CommunityBoardSection
        communityId={5}
        canWrite={false}
        onCreatePost={jest.fn()}
        onPostPress={jest.fn()}
      />
    );
    fireEvent.press(screen.getByLabelText('다시 시도'));

    expect(refetch).toHaveBeenCalled();
  });

  it('게시글을 탭하면 onPostPress가 호출된다', () => {
    const onPostPress = jest.fn();
    mockPosts({ data: page([NOTICE_POST]) });

    render(
      <CommunityBoardSection
        communityId={5}
        canWrite={false}
        onCreatePost={jest.fn()}
        onPostPress={onPostPress}
      />
    );
    fireEvent.press(screen.getByLabelText(/게시글: 이번 달 회비 안내/));

    expect(onPostPress).toHaveBeenCalledWith(NOTICE_POST.id);
  });

  it('글쓰기 CTA를 탭하면 onCreatePost가 호출된다', () => {
    const onCreatePost = jest.fn();
    mockPosts({ data: page([]) });

    render(
      <CommunityBoardSection
        communityId={5}
        canWrite
        onCreatePost={onCreatePost}
        onPostPress={jest.fn()}
      />
    );
    fireEvent.press(screen.getByLabelText('+ 글쓰기'));

    expect(onCreatePost).toHaveBeenCalled();
  });
});

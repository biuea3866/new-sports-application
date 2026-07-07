/**
 * usePosts(page, size, params)가 criteria를 searchPosts에 전달한다
 * useCreatePost는 communityId가 있으면 모임 게시판 캐시도 함께 무효화한다
 * useCommunityPosts가 모임 게시판 목록을 반환한다
 * useCommunityPosts는 403(PRIVATE 비멤버)이면 에러 상태로 전파된다(잠금 UI 분기용)
 * useComments가 댓글 페이지를 반환한다
 * useAddComment는 낙관적으로 댓글을 목록에 반영하고, 실패 시 롤백한다
 */
import { createElement } from 'react';
import { act } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react-native';

import {
  commentsQueryKey,
  communityPostsQueryKey,
  useAddComment,
  useComments,
  useCommunityPosts,
  useCreatePost,
  usePosts,
} from '../usePosts';
import type {
  CommentPageResponse,
  CommentResponse,
  CreatePostRequest,
  PageResponse,
  PostResponse,
} from '../../api/types';

jest.mock('../../api/post', () => ({
  searchPosts: jest.fn(),
  getPost: jest.fn(),
  createPost: jest.fn(),
  listCommunityPosts: jest.fn(),
  listComments: jest.fn(),
  addComment: jest.fn(),
}));

import {
  addComment,
  createPost,
  listComments,
  listCommunityPosts,
  searchPosts,
} from '../../api/post';

const searchPostsMock = searchPosts as jest.MockedFunction<typeof searchPosts>;
const createPostMock = createPost as jest.MockedFunction<typeof createPost>;
const listCommunityPostsMock = listCommunityPosts as jest.MockedFunction<typeof listCommunityPosts>;
const listCommentsMock = listComments as jest.MockedFunction<typeof listComments>;
const addCommentMock = addComment as jest.MockedFunction<typeof addComment>;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  return { wrapper, queryClient };
}

const globalPost: PostResponse = {
  id: 1,
  userId: 42,
  title: '전역 게시글',
  type: 'FREE',
  createdAt: '2026-07-07T00:00:00Z',
  communityId: null,
  sportCategory: 'SOCCER',
};

const communityPost: PostResponse = {
  id: 2,
  userId: 43,
  title: '모임 공지',
  type: 'NOTICE',
  createdAt: '2026-07-07T01:00:00Z',
  communityId: 5,
  sportCategory: 'SOCCER',
};

describe('usePosts', () => {
  afterEach(() => jest.clearAllMocks());

  it('sportCategory criteria를 searchPosts에 전달한다', async () => {
    const page: PageResponse<PostResponse> = {
      content: [globalPost],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    };
    searchPostsMock.mockResolvedValue(page);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => usePosts(0, 20, { sportCategory: 'SOCCER' }), {
      wrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(searchPostsMock).toHaveBeenCalledWith(0, 20, { sportCategory: 'SOCCER' });
  });

  it('기존 usePosts(page, size) 위치 인자 호출과 하위 호환된다', async () => {
    searchPostsMock.mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
    });
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => usePosts(0, 20), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(searchPostsMock).toHaveBeenCalledWith(0, 20, undefined);
  });
});

describe('useCreatePost', () => {
  afterEach(() => jest.clearAllMocks());

  it('communityId 없이 생성하면 전역 게시글 목록 캐시만 무효화된다', async () => {
    createPostMock.mockResolvedValue(globalPost);
    const { wrapper, queryClient } = createWrapper();
    const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useCreatePost(), { wrapper });

    const body: CreatePostRequest = { title: '전역 게시글', content: '본문' };
    await act(async () => {
      await result.current.mutateAsync(body);
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['posts'] });
  });

  it('communityId가 있으면 모임 게시판 캐시도 함께 무효화된다', async () => {
    createPostMock.mockResolvedValue(communityPost);
    const { wrapper, queryClient } = createWrapper();
    const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

    const { result } = renderHook(() => useCreatePost(), { wrapper });

    const body: CreatePostRequest = {
      title: '모임 공지',
      content: '본문',
      type: 'NOTICE',
      communityId: 5,
    };
    await act(async () => {
      await result.current.mutateAsync(body);
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['posts'] });
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: communityPostsQueryKey(5),
      exact: false,
    });
  });
});

describe('useCommunityPosts', () => {
  afterEach(() => jest.clearAllMocks());

  it('모임 게시판 목록을 반환한다', async () => {
    const page: PageResponse<PostResponse> = {
      content: [communityPost],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    };
    listCommunityPostsMock.mockResolvedValue(page);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useCommunityPosts(5), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.content).toEqual([communityPost]);
    expect(listCommunityPostsMock).toHaveBeenCalledWith(5, 0, 20, undefined);
  });

  it('PRIVATE 비멤버 조회 시(403) 에러 상태로 전파된다(잠금 UI 분기용)', async () => {
    listCommunityPostsMock.mockRejectedValue(
      Object.assign(new Error('Forbidden'), { status: 403 })
    );
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useCommunityPosts(9), { wrapper });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it('communityId가 0 이하면 쿼리를 실행하지 않는다', () => {
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useCommunityPosts(0), { wrapper });

    expect(result.current.fetchStatus).toBe('idle');
    expect(listCommunityPostsMock).not.toHaveBeenCalled();
  });
});

describe('useComments', () => {
  afterEach(() => jest.clearAllMocks());

  it('댓글 페이지를 반환한다', async () => {
    const commentPage: CommentPageResponse = {
      content: [
        { id: 1, postId: 1, userId: 42, content: '댓글', createdAt: '2026-07-07T00:00:00Z' },
      ],
      totalElements: 1,
      totalPages: 1,
      page: 0,
      size: 20,
    };
    listCommentsMock.mockResolvedValue(commentPage);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useComments(1), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.content).toHaveLength(1);
  });

  it('댓글 0건이면 빈 배열을 반환한다(정상)', async () => {
    listCommentsMock.mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      page: 0,
      size: 20,
    });
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useComments(1), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.content).toHaveLength(0);
  });
});

describe('useAddComment', () => {
  afterEach(() => jest.clearAllMocks());

  it('성공 시 새 댓글을 목록 캐시에 즉시(낙관적) 반영한다', async () => {
    const newComment: CommentResponse = {
      id: 10,
      postId: 1,
      userId: 42,
      content: '좋은 글이네요',
      createdAt: '2026-07-07T02:00:00Z',
    };
    // 최초 조회는 빈 목록, 성공 후 onSettled 재조회는 BE가 반영한 목록을 반환한다(실제 흐름 재현).
    listCommentsMock
      .mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 })
      .mockResolvedValueOnce({
        content: [newComment],
        totalElements: 1,
        totalPages: 1,
        page: 0,
        size: 20,
      });
    addCommentMock.mockResolvedValue(newComment);

    const { wrapper, queryClient } = createWrapper();
    const { result: commentsResult } = renderHook(() => useComments(1), { wrapper });
    await waitFor(() => expect(commentsResult.current.isSuccess).toBe(true));

    const { result } = renderHook(() => useAddComment(1), { wrapper });

    await act(async () => {
      await result.current.mutateAsync('좋은 글이네요');
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    const cached = queryClient.getQueryData<CommentPageResponse>(commentsQueryKey(1));
    expect(cached?.content.some((c) => c.content === '좋은 글이네요')).toBe(true);
  });

  it('실패 시 낙관적으로 추가했던 댓글을 롤백한다', async () => {
    listCommentsMock.mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      page: 0,
      size: 20,
    });
    addCommentMock.mockRejectedValue(Object.assign(new Error('Forbidden'), { status: 403 }));

    const { wrapper, queryClient } = createWrapper();
    const { result: commentsResult } = renderHook(() => useComments(1), { wrapper });
    await waitFor(() => expect(commentsResult.current.isSuccess).toBe(true));

    const { result } = renderHook(() => useAddComment(1), { wrapper });

    await act(async () => {
      await expect(result.current.mutateAsync('실패할 댓글')).rejects.toThrow('Forbidden');
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    const cached = queryClient.getQueryData<CommentPageResponse>(commentsQueryKey(1));
    expect(cached?.content.some((c) => c.content === '실패할 댓글')).toBe(false);
  });
});

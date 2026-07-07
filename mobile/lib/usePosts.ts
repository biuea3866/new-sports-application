/**
 * usePosts — GET /posts TanStack Query 훅 (sportCategory 등 criteria 확장)
 * usePost — GET /posts/{id} TanStack Query 훅
 * useCreatePost — POST /posts mutation 훅 (communityId 유무로 무효화 캐시 분기)
 * useCommunityPosts — GET /communities/{communityId}/posts TanStack Query 훅(모임 게시판)
 * useComments — GET /posts/{postId}/comments TanStack Query 훅
 * useAddComment — POST /posts/{postId}/comments mutation 훅(낙관적 업데이트)
 *
 * 서버 상태(게시글·댓글)는 Query 캐시가 SSOT — 스토어에 복사하지 않는다.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  addComment,
  createPost,
  getPost,
  listComments,
  listCommunityPosts,
  searchPosts,
} from '../api/post';
import type {
  CommentPageResponse,
  CommentResponse,
  CommunityPostSearchParams,
  CreatePostRequest,
  PageResponse,
  PostDetailResponse,
  PostResponse,
  PostSearchParams,
} from '../api/types';

export const POSTS_QUERY_KEY = ['posts'] as const;

export function communityPostsQueryKey(communityId: number) {
  return ['communities', communityId, 'posts'] as const;
}

export function commentsQueryKey(postId: number) {
  return ['posts', postId, 'comments'] as const;
}

export function usePosts(page = 0, size = 20, params?: PostSearchParams) {
  return useQuery<PageResponse<PostResponse>, Error>({
    queryKey: [...POSTS_QUERY_KEY, page, size, params ?? null],
    queryFn: () => searchPosts(page, size, params),
  });
}

export function usePost(id: number) {
  return useQuery<PostDetailResponse, Error>({
    queryKey: [...POSTS_QUERY_KEY, id],
    queryFn: () => getPost(id),
    enabled: id > 0,
  });
}

/**
 * `communityId`가 있는 게시글을 생성하면(모임 게시글) 전역 목록 캐시와 함께 해당 모임
 * 게시판 캐시(`communityPostsQueryKey`)도 무효화한다. 페이지네이션·criteria별로 쌓인
 * 하위 쿼리키를 모두 지우기 위해 `exact: false`로 무효화한다.
 */
export function useCreatePost() {
  const queryClient = useQueryClient();

  return useMutation<PostResponse, Error, CreatePostRequest>({
    mutationFn: (body) => createPost(body),
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: POSTS_QUERY_KEY });
      if (variables.communityId != null) {
        void queryClient.invalidateQueries({
          queryKey: communityPostsQueryKey(variables.communityId),
          exact: false,
        });
      }
    },
  });
}

/** `GET /communities/{communityId}/posts?sportCategory=&page=&size=` — 모임 게시판. */
export function useCommunityPosts(
  communityId: number,
  page = 0,
  size = 20,
  params?: CommunityPostSearchParams
) {
  return useQuery<PageResponse<PostResponse>, Error>({
    queryKey: [...communityPostsQueryKey(communityId), page, size, params ?? null],
    queryFn: () => listCommunityPosts(communityId, page, size, params),
    enabled: communityId > 0,
  });
}

/**
 * `GET /posts/{postId}/comments?page=&size=`. 캐시 키는 `commentsQueryKey(postId)` 고정 —
 * `useAddComment`의 낙관적 업데이트가 동일 키를 갱신할 수 있어야 하므로 page/size는
 * 쿼리 함수 인자로만 쓰고 키에는 포함하지 않는다(댓글 목록은 깊은 페이지네이션을 갖지 않는
 * 화면 특성상 단일 캐시로 충분, design-fe-app A-P4).
 */
export function useComments(postId: number, page = 0, size = 20) {
  return useQuery<CommentPageResponse, Error>({
    queryKey: commentsQueryKey(postId),
    queryFn: () => listComments(postId, page, size),
    enabled: postId > 0,
  });
}

/**
 * `POST /posts/{postId}/comments` — 입력 즉시 댓글 목록에 낙관적으로 반영하고(design-fe-app
 * "상태관리 설계" — 댓글 작성만 낙관 적용), 실패 시 이전 캐시로 롤백한다.
 */
export function useAddComment(postId: number) {
  const queryClient = useQueryClient();
  const queryKey = commentsQueryKey(postId);

  return useMutation<CommentResponse, Error, string, { previous?: CommentPageResponse }>({
    mutationFn: (content) => addComment(postId, content),
    onMutate: async (content) => {
      await queryClient.cancelQueries({ queryKey });
      const previous = queryClient.getQueryData<CommentPageResponse>(queryKey);
      const optimisticComment: CommentResponse = {
        id: -Date.now(),
        postId,
        userId: -1,
        content,
        createdAt: new Date().toISOString(),
      };
      if (previous) {
        queryClient.setQueryData<CommentPageResponse>(queryKey, {
          ...previous,
          content: [optimisticComment, ...previous.content],
          totalElements: previous.totalElements + 1,
        });
      }
      return { previous };
    },
    onError: (_error, _content, context) => {
      if (context?.previous) {
        queryClient.setQueryData<CommentPageResponse>(queryKey, context.previous);
      }
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: commentsQueryKey(postId) });
    },
  });
}

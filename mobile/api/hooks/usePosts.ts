/**
 * usePosts.ts — 커뮤니티 게시글 도메인 react-query 훅
 */
import {
  useQuery,
  useMutation,
  useQueryClient,
  type UseQueryOptions,
  type UseMutationOptions,
} from '@tanstack/react-query';
import {
  getPosts,
  getPostById,
  createPost,
  getPostComments,
  createComment,
  updateComment,
  deleteComment,
  type PostListParams,
  type PostDto,
  type PostDetailDto,
  type CreatePostRequest,
  type CommentDto,
  type CreateCommentRequest,
  type UpdateCommentRequest,
} from '../posts';
import { type PageResponse } from '../facilities';
import { postsKeys } from '../queryKeys';

export function usePostsQuery(
  params?: PostListParams,
  options?: Omit<UseQueryOptions<PageResponse<PostDto>>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: postsKeys.list(params ?? {}),
    queryFn: () => getPosts(params),
    ...options,
  });
}

export function usePostDetailQuery(
  id: number,
  options?: Omit<UseQueryOptions<PostDetailDto>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: postsKeys.detail(id),
    queryFn: () => getPostById(id),
    enabled: id > 0,
    ...options,
  });
}

export function usePostCommentsQuery(
  postId: number,
  options?: Omit<UseQueryOptions<CommentDto[]>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: postsKeys.comments(postId),
    queryFn: () => getPostComments(postId),
    enabled: postId > 0,
    ...options,
  });
}

export function useCreatePostMutation(
  options?: UseMutationOptions<PostDetailDto, Error, CreatePostRequest>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createPost,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: postsKeys.lists() });
    },
    ...options,
  });
}

export function useCreateCommentMutation(
  postId: number,
  options?: UseMutationOptions<CommentDto, Error, CreateCommentRequest>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateCommentRequest) => createComment(postId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: postsKeys.comments(postId) });
      queryClient.invalidateQueries({ queryKey: postsKeys.detail(postId) });
    },
    ...options,
  });
}

export function useUpdateCommentMutation(
  postId: number,
  options?: UseMutationOptions<CommentDto, Error, { commentId: number; request: UpdateCommentRequest }>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ commentId, request }) => updateComment(postId, commentId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: postsKeys.comments(postId) });
    },
    ...options,
  });
}

export function useDeleteCommentMutation(
  postId: number,
  options?: UseMutationOptions<void, Error, number>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (commentId: number) => deleteComment(postId, commentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: postsKeys.comments(postId) });
      queryClient.invalidateQueries({ queryKey: postsKeys.detail(postId) });
    },
    ...options,
  });
}

/**
 * usePosts — GET /posts TanStack Query 훅
 * usePost — GET /posts/{id} TanStack Query 훅
 * useCreatePost — POST /posts mutation 훅
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createPost, getPost, searchPosts } from '../api/post';
import type {
  CreatePostRequest,
  PageResponse,
  PostDetailResponse,
  PostResponse,
} from '../api/types';

export const POSTS_QUERY_KEY = ['posts'] as const;

export function usePosts(page = 0, size = 20) {
  return useQuery<PageResponse<PostResponse>, Error>({
    queryKey: [...POSTS_QUERY_KEY, page, size],
    queryFn: () => searchPosts(page, size),
  });
}

export function usePost(id: number) {
  return useQuery<PostDetailResponse, Error>({
    queryKey: [...POSTS_QUERY_KEY, id],
    queryFn: () => getPost(id),
    enabled: id > 0,
  });
}

export function useCreatePost() {
  const queryClient = useQueryClient();

  return useMutation<PostResponse, Error, CreatePostRequest>({
    mutationFn: (body) => createPost(body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: POSTS_QUERY_KEY });
    },
  });
}

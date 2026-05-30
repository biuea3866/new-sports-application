/**
 * post.ts — 게시글 API 함수
 */
import { getBeClient } from './be-client';
import type {
  CreatePostRequest,
  PageResponse,
  PostDetailResponse,
  PostResponse,
} from './types';

export async function searchPosts(page = 0, size = 20): Promise<PageResponse<PostResponse>> {
  const res = await getBeClient().get<PageResponse<PostResponse>>('/posts', {
    params: { page, size },
  });
  return res.data;
}

export async function getPost(id: number): Promise<PostDetailResponse> {
  const res = await getBeClient().get<PostDetailResponse>(`/posts/${id}`);
  return res.data;
}

export async function createPost(body: CreatePostRequest): Promise<PostResponse> {
  const res = await getBeClient().post<PostResponse>('/posts', body);
  return res.data;
}

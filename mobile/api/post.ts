/**
 * post.ts — 게시글·모임 게시판·댓글 API 함수
 *
 * 근거: `20260707-post-community-연동-tdd.md` "API 계약", BE
 * `PostApiController`·`CommunityPostApiController`·`CommentApiController`.
 * 컴포넌트에서 직접 호출 금지 — `lib/usePosts.ts` 훅을 통해서만 사용한다.
 */
import { getBeClient } from './be-client';
import type {
  CommentPageResponse,
  CommentResponse,
  CommunityPostSearchParams,
  CreatePostRequest,
  PageResponse,
  PostDetailResponse,
  PostResponse,
  PostSearchParams,
} from './types';

/** `GET /posts?type=&userId=&keyword=&communityId=&sportCategory=&page=&size=` */
export async function searchPosts(
  page = 0,
  size = 20,
  params?: PostSearchParams
): Promise<PageResponse<PostResponse>> {
  const res = await getBeClient().get<PageResponse<PostResponse>>('/posts', {
    params: { page, size, ...params },
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

/** `GET /communities/{communityId}/posts?sportCategory=&page=&size=` — 모임 게시판 목록. */
export async function listCommunityPosts(
  communityId: number,
  page = 0,
  size = 20,
  params?: CommunityPostSearchParams
): Promise<PageResponse<PostResponse>> {
  const res = await getBeClient().get<PageResponse<PostResponse>>(
    `/communities/${communityId}/posts`,
    { params: { page, size, ...params } }
  );
  return res.data;
}

/** `GET /posts/{postId}/comments?page=&size=` */
export async function listComments(
  postId: number,
  page = 0,
  size = 20
): Promise<CommentPageResponse> {
  const res = await getBeClient().get<CommentPageResponse>(`/posts/${postId}/comments`, {
    params: { page, size },
  });
  return res.data;
}

/** `POST /posts/{postId}/comments` */
export async function addComment(postId: number, content: string): Promise<CommentResponse> {
  const res = await getBeClient().post<CommentResponse>(`/posts/${postId}/comments`, {
    content,
  });
  return res.data;
}

/** `DELETE /comments/{id}` */
export async function deleteComment(id: number): Promise<void> {
  await getBeClient().delete(`/comments/${id}`);
}

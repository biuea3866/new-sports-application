/**
 * posts.ts — 커뮤니티 게시글 도메인 API 함수
 *
 * BE 경로:
 *   GET    /posts                          — 게시글 목록
 *   GET    /posts/{id}                     — 게시글 상세
 *   POST   /posts                          — 게시글 작성
 *   GET    /posts/{postId}/comments        — 댓글 목록
 *   POST   /posts/{postId}/comments        — 댓글 작성
 *   PATCH  /posts/{postId}/comments/{id}   — 댓글 수정
 *   DELETE /posts/{postId}/comments/{id}   — 댓글 삭제
 */
import { getBeClient } from './be-client';
import { PATHS } from './paths';
import { type PageResponse } from './facilities';

// ─── DTO 타입 ────────────────────────────────────────────────────────────────

export interface PostListParams {
  keyword?: string;
  category?: string;
  page?: number;
  size?: number;
}

export interface PostDto {
  id: number;
  title: string;
  category: string;
  authorId: number;
  authorName: string;
  commentCount: number;
  likeCount: number;
  createdAt: string;
}

export interface PostDetailDto extends PostDto {
  content: string;
  imageUrls: string[];
}

export interface CreatePostRequest {
  title: string;
  content: string;
  category: string;
  imageUrls?: string[];
}

export interface CommentDto {
  id: number;
  postId: number;
  authorId: number;
  authorName: string;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCommentRequest {
  content: string;
}

export interface UpdateCommentRequest {
  content: string;
}

// ─── API 함수 ────────────────────────────────────────────────────────────────

export async function getPosts(params?: PostListParams): Promise<PageResponse<PostDto>> {
  const response = await getBeClient().get<PageResponse<PostDto>>(PATHS.posts, { params });
  return response.data;
}

export async function getPostById(id: number): Promise<PostDetailDto> {
  const response = await getBeClient().get<PostDetailDto>(PATHS.postById(id));
  return response.data;
}

export async function createPost(request: CreatePostRequest): Promise<PostDetailDto> {
  const response = await getBeClient().post<PostDetailDto>(PATHS.posts, request);
  return response.data;
}

export async function getPostComments(postId: number): Promise<CommentDto[]> {
  const response = await getBeClient().get<CommentDto[]>(PATHS.postComments(postId));
  return response.data;
}

export async function createComment(
  postId: number,
  request: CreateCommentRequest
): Promise<CommentDto> {
  const response = await getBeClient().post<CommentDto>(PATHS.postComments(postId), request);
  return response.data;
}

export async function updateComment(
  postId: number,
  commentId: number,
  request: UpdateCommentRequest
): Promise<CommentDto> {
  const response = await getBeClient().patch<CommentDto>(
    PATHS.postCommentById(postId, commentId),
    request
  );
  return response.data;
}

export async function deleteComment(postId: number, commentId: number): Promise<void> {
  await getBeClient().delete(PATHS.postCommentById(postId, commentId));
}

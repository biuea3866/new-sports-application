/**
 * U-01: GET /posts 성공 시 게시글 목록 페이지를 반환한다
 * U-02: GET /posts/{id} 성공 시 게시글 상세를 반환한다
 * U-03: POST /posts 성공 시 생성된 게시글을 반환한다
 * U-04: GET /posts/{id} 존재하지 않는 게시글 조회 시 404 에러가 발생한다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../../api/be-client';
import type {
  CreatePostRequest,
  PageResponse,
  PostDetailResponse,
  PostResponse,
} from '../../api/types';

// expo-secure-store, expo-router는 jest.setup.ts의 global mock에서 처리

describe('Post API', () => {
  const client = createBeClient('http://localhost:8080');
  const mock = new MockAdapter(client);

  afterEach(() => mock.reset());

  const mockPost: PostResponse = {
    id: 1,
    userId: 42,
    title: '테스트 게시글',
    type: 'FREE',
    createdAt: '2026-05-30T00:00:00Z',
  };

  const mockPostDetail: PostDetailResponse = {
    id: 1,
    userId: 42,
    title: '테스트 게시글',
    content: '게시글 내용입니다.',
    type: 'FREE',
    createdAt: '2026-05-30T00:00:00Z',
    comments: [],
  };

  describe('U-01: searchPosts', () => {
    it('GET /posts 호출 시 게시글 목록 페이지를 반환한다', async () => {
      const mockPage: PageResponse<PostResponse> = {
        content: [mockPost],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 20,
      };
      mock.onGet('/posts').reply(200, mockPage);

      const res = await client.get<PageResponse<PostResponse>>('/posts', {
        params: { page: 0, size: 20 },
      });

      expect(res.data.content).toHaveLength(1);
      expect(res.data.content[0].id).toBe(1);
      expect(res.data.content[0].title).toBe('테스트 게시글');
      expect(res.data.totalElements).toBe(1);
    });
  });

  describe('U-02: getPost', () => {
    it('GET /posts/1 호출 시 게시글 상세를 반환한다', async () => {
      mock.onGet('/posts/1').reply(200, mockPostDetail);

      const res = await client.get<PostDetailResponse>('/posts/1');

      expect(res.data.id).toBe(1);
      expect(res.data.title).toBe('테스트 게시글');
      expect(res.data.content).toBe('게시글 내용입니다.');
      expect(res.data.comments).toHaveLength(0);
    });
  });

  describe('U-03: createPost', () => {
    it('POST /posts 호출 시 생성된 게시글을 반환한다', async () => {
      mock.onPost('/posts').reply(201, mockPost);

      const body: CreatePostRequest = {
        title: '테스트 게시글',
        content: '게시글 내용입니다.',
      };
      const res = await client.post<PostResponse>('/posts', body);

      expect(res.data.id).toBe(1);
      expect(res.data.title).toBe('테스트 게시글');
    });
  });

  describe('U-04: 없는 게시글 조회', () => {
    it('GET /posts/999 호출 시 404 에러가 발생한다', async () => {
      mock.onGet('/posts/999').reply(404, { message: 'Post not found' });

      await expect(client.get('/posts/999')).rejects.toThrow();
    });
  });
});

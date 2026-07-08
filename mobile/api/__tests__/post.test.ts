/**
 * U-01: searchPosts는 GET /posts에 page·size·criteria를 쿼리 파라미터로 전달한다
 * U-02: getPost는 GET /posts/{id}로 상세를 반환한다
 * U-03: createPost는 communityId 유무와 무관하게 POST /posts로 요청한다
 * U-04: listCommunityPosts는 GET /communities/{id}/posts로 모임 게시판 목록을 반환한다
 * U-05: listComments는 GET /posts/{id}/comments로 댓글 페이지를 반환한다
 * U-06: addComment는 POST /posts/{id}/comments로 댓글을 생성한다
 * U-07: deleteComment는 DELETE /comments/{id}를 호출한다
 * U-08: 실패 응답(404/403)은 예외로 전파된다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../be-client';
import {
  addComment,
  createPost,
  deleteComment,
  getPost,
  listComments,
  listCommunityPosts,
  searchPosts,
} from '../post';
import type {
  CommentPageResponse,
  CommentResponse,
  CreatePostRequest,
  PageResponse,
  PostDetailResponse,
  PostResponse,
} from '../types';

jest.mock('../be-client', () => {
  const actual = jest.requireActual<typeof import('../be-client')>('../be-client');
  const instance = actual.createBeClient('http://localhost:8080');
  return {
    ...actual,
    getBeClient: jest.fn(() => instance),
    _testInstance: instance,
  };
});

import * as beClientModule from '../be-client';

const testInstance = (
  beClientModule as unknown as { _testInstance: ReturnType<typeof createBeClient> }
)._testInstance;
const mock = new MockAdapter(testInstance);

afterEach(() => mock.reset());

const mockGlobalPost: PostResponse = {
  id: 1,
  userId: 42,
  title: '전역 게시글',
  type: 'FREE',
  createdAt: '2026-07-07T00:00:00Z',
  communityId: null,
  sportCategory: 'SOCCER',
};

const mockCommunityPost: PostResponse = {
  id: 2,
  userId: 43,
  title: '모임 공지',
  type: 'NOTICE',
  createdAt: '2026-07-07T01:00:00Z',
  communityId: 5,
  sportCategory: 'SOCCER',
};

describe('U-01: searchPosts', () => {
  it('GET /posts 호출 시 page·size·criteria를 쿼리 파라미터로 전달한다', async () => {
    const mockPage: PageResponse<PostResponse> = {
      content: [mockGlobalPost],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    };
    mock.onGet('/posts').reply((config) => {
      expect(config.params).toMatchObject({
        page: 0,
        size: 20,
        sportCategory: 'SOCCER',
      });
      return [200, mockPage];
    });

    const res = await searchPosts(0, 20, { sportCategory: 'SOCCER' });

    expect(res.content).toHaveLength(1);
    expect(res.content[0].communityId).toBeNull();
  });

  it('criteria 없이 호출해도 동작한다(전체 종목)', async () => {
    const mockPage: PageResponse<PostResponse> = {
      content: [mockGlobalPost, mockCommunityPost],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 20,
    };
    mock.onGet('/posts').reply(200, mockPage);

    const res = await searchPosts();

    expect(res.content).toHaveLength(2);
  });
});

describe('U-02: getPost', () => {
  it('GET /posts/1 호출 시 게시글 상세를 반환한다', async () => {
    const detail: PostDetailResponse = {
      id: 1,
      userId: 42,
      title: '전역 게시글',
      content: '본문',
      type: 'FREE',
      createdAt: '2026-07-07T00:00:00Z',
      comments: [],
      communityId: null,
      sportCategory: 'SOCCER',
    };
    mock.onGet('/posts/1').reply(200, detail);

    const res = await getPost(1);

    expect(res.title).toBe('전역 게시글');
    expect(res.comments).toHaveLength(0);
  });

  it('U-08 존재하지 않는 게시글 조회 시 404 에러가 발생한다', async () => {
    mock.onGet('/posts/999').reply(404, { message: 'Post not found' });

    await expect(getPost(999)).rejects.toThrow();
  });
});

describe('U-03: createPost', () => {
  it('communityId 없이 호출하면 전역 게시글 생성 요청을 보낸다', async () => {
    mock.onPost('/posts').reply(201, mockGlobalPost);

    const body: CreatePostRequest = { title: '전역 게시글', content: '본문' };
    const res = await createPost(body);

    expect(res.id).toBe(1);
    expect(res.communityId).toBeNull();
  });

  it('communityId를 포함하면 모임 게시글 생성 요청을 보낸다', async () => {
    mock.onPost('/posts').reply(201, mockCommunityPost);

    const body: CreatePostRequest = {
      title: '모임 공지',
      content: '본문',
      type: 'NOTICE',
      communityId: 5,
    };
    const res = await createPost(body);

    expect(res.communityId).toBe(5);
    expect(res.type).toBe('NOTICE');
  });

  it('U-08 작성 권한 없음(403)은 예외로 전파된다', async () => {
    mock.onPost('/posts').reply(403, { message: 'Forbidden' });

    await expect(createPost({ title: 't', content: 'c', communityId: 5 })).rejects.toThrow();
  });
});

describe('U-04: listCommunityPosts', () => {
  it('GET /communities/5/posts 호출 시 모임 게시판 목록을 반환한다', async () => {
    const mockPage: PageResponse<PostResponse> = {
      content: [mockCommunityPost],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    };
    mock.onGet('/communities/5/posts').reply(200, mockPage);

    const res = await listCommunityPosts(5);

    expect(res.content).toHaveLength(1);
    expect(res.content[0].communityId).toBe(5);
  });

  it('sportCategory 필터를 쿼리 파라미터로 전달한다', async () => {
    mock.onGet('/communities/5/posts').reply((config) => {
      expect(config.params).toMatchObject({ sportCategory: 'BASKETBALL' });
      return [200, { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }];
    });

    const res = await listCommunityPosts(5, 0, 20, { sportCategory: 'BASKETBALL' });

    expect(res.content).toHaveLength(0);
  });

  it('U-08 비멤버 조회(PRIVATE) 시 403 에러가 발생한다', async () => {
    mock.onGet('/communities/9/posts').reply(403, { message: 'Forbidden' });

    await expect(listCommunityPosts(9)).rejects.toThrow();
  });
});

describe('U-05: listComments', () => {
  it('GET /posts/1/comments 호출 시 댓글 페이지를 반환한다', async () => {
    const commentPage: CommentPageResponse = {
      content: [
        { id: 1, postId: 1, userId: 42, content: '댓글', createdAt: '2026-07-07T00:00:00Z' },
      ],
      totalElements: 1,
      totalPages: 1,
      page: 0,
      size: 20,
    };
    mock.onGet('/posts/1/comments').reply(200, commentPage);

    const res = await listComments(1);

    expect(res.content).toHaveLength(1);
    expect(res.page).toBe(0);
  });

  it('댓글 0건이면 빈 배열을 반환한다', async () => {
    mock.onGet('/posts/1/comments').reply(200, {
      content: [],
      totalElements: 0,
      totalPages: 0,
      page: 0,
      size: 20,
    });

    const res = await listComments(1);

    expect(res.content).toHaveLength(0);
  });
});

describe('U-06: addComment', () => {
  it('POST /posts/1/comments 호출 시 생성된 댓글을 반환한다', async () => {
    const comment: CommentResponse = {
      id: 10,
      postId: 1,
      userId: 42,
      content: '좋은 글이네요',
      createdAt: '2026-07-07T02:00:00Z',
    };
    mock.onPost('/posts/1/comments').reply(201, comment);

    const res = await addComment(1, '좋은 글이네요');

    expect(res.content).toBe('좋은 글이네요');
    expect(JSON.parse(mock.history.post[0].data as string)).toEqual({
      content: '좋은 글이네요',
    });
  });

  it('U-08 비멤버 작성 시 403 에러가 발생한다', async () => {
    mock.onPost('/posts/9/comments').reply(403, { message: 'Forbidden' });

    await expect(addComment(9, '댓글')).rejects.toThrow();
  });
});

describe('U-07: deleteComment', () => {
  it('DELETE /comments/10 호출 시 204를 반환한다', async () => {
    mock.onDelete('/comments/10').reply(204);

    await expect(deleteComment(10)).resolves.toBeUndefined();
  });
});

/**
 * U-01: getUnreadCounts가 GET /rooms/me/unread 호출 시 RoomUnreadResponse[]를 반환한다
 * U-02: markRead가 POST /rooms/{id}/read 호출 시 lastReadMessageId를 본문으로 보낸다
 * U-03: backfillMessages가 afterMessageId 이후 메시지만 반환한다(경계값)
 * U-04: startGoodsChat이 POST /products/{id}/chat 호출 시 RoomResponse를 반환한다
 * U-05: evictGuest가 POST /rooms/{id}/guests/{userId}/evict 호출 시 204를 반환한다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../be-client';
import type { RoomUnreadResponse } from '../chat-types';
import type { MessageResponse, RoomResponse } from '../types';

// be-client singleton 대신 테스트용 인스턴스를 주입하기 위해 모듈을 mock 처리
jest.mock('../be-client', () => {
  const actual = jest.requireActual<typeof import('../be-client')>('../be-client');
  return {
    ...actual,
    getBeClient: jest.fn(),
  };
});

import { getBeClient } from '../be-client';
import { backfillMessages, evictGuest, getUnreadCounts, markRead, startGoodsChat } from '../chat';

const mockedGetBeClient = jest.mocked(getBeClient);

describe('chat API', () => {
  let mockAdapter: MockAdapter;

  beforeEach(() => {
    const instance = createBeClient('http://localhost:8080');
    mockAdapter = new MockAdapter(instance);
    mockedGetBeClient.mockReturnValue(instance);
  });

  afterEach(() => {
    mockAdapter.restore();
    jest.clearAllMocks();
  });

  describe('U-01: getUnreadCounts', () => {
    it('GET /rooms/me/unread 호출 시 방별 안읽은 수 목록을 반환한다', async () => {
      const response: RoomUnreadResponse[] = [
        { roomId: 1, unreadCount: 3 },
        { roomId: 2, unreadCount: 0 },
      ];
      mockAdapter.onGet('/rooms/me/unread').reply(200, response);

      const result = await getUnreadCounts();

      expect(result).toEqual(response);
    });
  });

  describe('U-02: markRead', () => {
    it('POST /rooms/1/read 호출 시 lastReadMessageId를 본문으로 전송하고 UnreadResponse를 반환한다', async () => {
      const response: RoomUnreadResponse = { roomId: 1, unreadCount: 0 };
      mockAdapter.onPost('/rooms/1/read').reply(200, response);

      const result = await markRead(1, 42);

      expect(result).toEqual(response);
      expect(JSON.parse(mockAdapter.history.post[0].data as string)).toEqual({
        lastReadMessageId: 42,
      });
    });
  });

  describe('U-03: backfillMessages 경계값', () => {
    it('afterMessageId 쿼리 파라미터로 그 이후 메시지만 반환한다', async () => {
      const afterOnly: MessageResponse[] = [
        { id: 12, roomId: 1, senderId: 3, content: '이후 메시지', sentAt: '2026-07-04T09:01:00Z' },
      ];
      mockAdapter.onGet('/rooms/1/messages/backfill').reply(200, afterOnly);

      const result = await backfillMessages(1, 11);

      expect(result).toEqual(afterOnly);
      expect(result.every((message) => message.id > 11)).toBe(true);
      expect(mockAdapter.history.get[0].params).toEqual({ afterMessageId: 11 });
    });
  });

  describe('U-04: startGoodsChat', () => {
    it('POST /products/100/chat 호출 시 RoomResponse를 반환한다', async () => {
      const response: RoomResponse = { id: 5, type: 'DIRECT', name: null };
      mockAdapter.onPost('/products/100/chat').reply(201, response);

      const result = await startGoodsChat(100);

      expect(result).toEqual(response);
    });
  });

  describe('U-05: evictGuest', () => {
    it('POST /rooms/1/guests/9/evict 호출 시 204를 반환한다', async () => {
      mockAdapter.onPost('/rooms/1/guests/9/evict').reply(204);

      await expect(evictGuest(1, 9)).resolves.toBeUndefined();
    });
  });
});

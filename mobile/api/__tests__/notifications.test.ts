/**
 * U-01: GET /notifications/me 성공 시 알림 목록을 반환한다
 * U-02: GET /notifications/me 페이징 파라미터가 정확히 전달된다
 * U-03: GET /notifications/me/unread-count 성공 시 unreadCount 필드를 반환한다
 * U-04: PATCH /notifications/{id}/read 호출 시 204 응답 처리가 성공한다
 * U-05: GET /notifications/me 500 응답 시 에러가 발생한다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../be-client';
import type {
  NotificationListResponse,
  NotificationResponse,
  UnreadCountResponse,
} from '../types';

describe('Notification API', () => {
  const client = createBeClient('http://localhost:8080');
  const mock = new MockAdapter(client);

  afterEach(() => mock.reset());

  const mockNotification: NotificationResponse = {
    id: 1,
    title: '예약이 확정되었습니다',
    content: '잠실 야구장 2024-10-20 예약이 확정되었습니다.',
    type: 'BOOKING',
    isRead: false,
    readAt: null,
    createdAt: '2024-10-19T10:00:00Z',
  };

  const mockListResponse: NotificationListResponse = {
    content: [mockNotification],
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 20,
  };

  describe('U-01: getMyNotifications', () => {
    it('GET /notifications/me 호출 시 알림 목록 페이지를 반환한다', async () => {
      mock.onGet('/notifications/me').reply(200, mockListResponse);

      const res = await client.get<NotificationListResponse>('/notifications/me', {
        params: { page: 0, size: 20 },
      });

      expect(res.data.content).toHaveLength(1);
      expect(res.data.content[0].id).toBe(1);
      expect(res.data.content[0].isRead).toBe(false);
      expect(res.data.content[0].type).toBe('BOOKING');
      expect(res.data.totalElements).toBe(1);
    });
  });

  describe('U-02: getMyNotifications paging', () => {
    it('page=1, size=10 파라미터가 포함된 GET /notifications/me 요청이 성공한다', async () => {
      const secondPageResponse: NotificationListResponse = {
        ...mockListResponse,
        number: 1,
        size: 10,
      };
      mock.onGet('/notifications/me').reply(200, secondPageResponse);

      const res = await client.get<NotificationListResponse>('/notifications/me', {
        params: { page: 1, size: 10 },
      });

      expect(res.data.number).toBe(1);
      expect(res.data.size).toBe(10);
    });
  });

  describe('U-03: getUnreadCount', () => {
    it('GET /notifications/me/unread-count 호출 시 unreadCount 필드를 반환한다', async () => {
      const mockUnread: UnreadCountResponse = { unreadCount: 3 };
      mock.onGet('/notifications/me/unread-count').reply(200, mockUnread);

      const res = await client.get<UnreadCountResponse>('/notifications/me/unread-count');

      expect(res.data.unreadCount).toBe(3);
    });
  });

  describe('U-04: markNotificationRead', () => {
    it('PATCH /notifications/1/read 호출 시 성공 응답을 반환한다', async () => {
      mock.onPatch('/notifications/1/read').reply(204);

      const res = await client.patch('/notifications/1/read');

      expect(res.status).toBe(204);
    });
  });

  describe('U-05: getMyNotifications error', () => {
    it('GET /notifications/me 500 응답 시 에러가 발생한다', async () => {
      mock.onGet('/notifications/me').reply(500, { message: 'Internal Server Error' });

      await expect(
        client.get('/notifications/me', { params: { page: 0, size: 20 } })
      ).rejects.toThrow();
    });
  });
});

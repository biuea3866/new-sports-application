/**
 * U-01: GET /rooms/me 성공 시 방 목록을 반환한다
 * U-02: GET /rooms/{id}/messages 성공 시 메시지 목록과 nextCursor를 반환한다
 * U-03: POST /rooms/{id}/messages 성공 시 전송된 메시지를 반환한다
 * U-04: GET /rooms/me 실패 시 에러가 발생한다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../../api/be-client';
import type { ListMessagesResponse, MessageResponse, RoomResponse } from '../../api/types';

// expo-secure-store, expo-router는 jest.setup.ts의 global mock에서 처리

describe('Room API', () => {
  const client = createBeClient('http://localhost:8080');
  const mock = new MockAdapter(client);

  afterEach(() => mock.reset());

  const mockRoom: RoomResponse = {
    id: 1,
    type: 'DIRECT',
    name: null,
  };

  const mockMessage: MessageResponse = {
    id: 10,
    roomId: 1,
    senderId: 42,
    content: '안녕하세요',
    sentAt: '2024-01-01T09:00:00Z',
  };

  describe('U-01: listMyRooms', () => {
    it('GET /rooms/me 호출 시 방 목록을 반환한다', async () => {
      mock.onGet('/rooms/me').reply(200, [mockRoom]);

      const res = await client.get<RoomResponse[]>('/rooms/me');

      expect(res.data).toHaveLength(1);
      expect(res.data[0].id).toBe(1);
      expect(res.data[0].type).toBe('DIRECT');
      expect(res.data[0].name).toBeNull();
    });
  });

  describe('U-02: listMessages', () => {
    it('GET /rooms/1/messages 호출 시 메시지 목록과 nextCursor를 반환한다', async () => {
      const mockResponse: ListMessagesResponse = {
        messages: [mockMessage],
        nextCursor: null,
      };
      mock.onGet('/rooms/1/messages').reply(200, mockResponse);

      const res = await client.get<ListMessagesResponse>('/rooms/1/messages');

      expect(res.data.messages).toHaveLength(1);
      expect(res.data.messages[0].content).toBe('안녕하세요');
      expect(res.data.nextCursor).toBeNull();
    });
  });

  describe('U-03: sendMessage', () => {
    it('POST /rooms/1/messages 호출 시 전송된 메시지를 반환한다', async () => {
      mock.onPost('/rooms/1/messages').reply(201, mockMessage);

      const res = await client.post<MessageResponse>('/rooms/1/messages', {
        content: '안녕하세요',
      });

      expect(res.data.id).toBe(10);
      expect(res.data.content).toBe('안녕하세요');
      expect(res.data.senderId).toBe(42);
    });
  });

  describe('U-04: listMyRooms 실패', () => {
    it('GET /rooms/me 서버 오류 시 에러가 발생한다', async () => {
      mock.onGet('/rooms/me').reply(500, { message: 'Internal Server Error' });

      await expect(client.get('/rooms/me')).rejects.toThrow();
    });
  });
});

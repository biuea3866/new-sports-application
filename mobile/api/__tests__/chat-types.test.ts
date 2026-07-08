/**
 * chat-types 정규화 유틸·유니온 값 테스트
 */
import {
  normalizeBroadcastMessage,
  type BroadcastMessage,
  type ChatMessage,
  type InvitationStatus,
  type RoomUnreadResponse,
} from '../chat-types';

describe('normalizeBroadcastMessage', () => {
  it('BroadcastMessage를 ChatMessage로 정규화하면 messageId가 id로 매핑된다', () => {
    const broadcast: BroadcastMessage = {
      messageId: 101,
      userId: 7,
      content: '안녕하세요',
      createdAt: '2026-07-04T10:00:00+09:00',
    };

    const chatMessage: ChatMessage = normalizeBroadcastMessage(broadcast, 55);

    expect(chatMessage.id).toBe(101);
  });

  it('BroadcastMessage를 ChatMessage로 정규화하면 userId가 senderId로 매핑된다', () => {
    const broadcast: BroadcastMessage = {
      messageId: 101,
      userId: 7,
      content: '안녕하세요',
      createdAt: '2026-07-04T10:00:00+09:00',
    };

    const chatMessage = normalizeBroadcastMessage(broadcast, 55);

    expect(chatMessage.senderId).toBe(7);
  });

  it('BroadcastMessage를 ChatMessage로 정규화하면 createdAt이 sentAt으로 매핑되고 content·roomId가 그대로 유지된다', () => {
    const broadcast: BroadcastMessage = {
      messageId: 101,
      userId: 7,
      content: '안녕하세요',
      createdAt: '2026-07-04T10:00:00+09:00',
    };

    const chatMessage = normalizeBroadcastMessage(broadcast, 55);

    expect(chatMessage.sentAt).toBe('2026-07-04T10:00:00+09:00');
    expect(chatMessage.content).toBe('안녕하세요');
    expect(chatMessage.roomId).toBe(55);
  });
});

describe('InvitationStatus', () => {
  it('BE 상태 전이 표의 5개 값과 정확히 일치한다', () => {
    const validStatuses: InvitationStatus[] = [
      'PENDING',
      'ACCEPTED',
      'REJECTED',
      'REVOKED',
      'EXPIRED',
    ];

    expect(validStatuses).toHaveLength(5);
  });
});

describe('RoomUnreadResponse', () => {
  it('GET /rooms/me/unread 계약과 필드가 일치한다', () => {
    const response: RoomUnreadResponse = {
      roomId: 1,
      unreadCount: 3,
    };

    expect(response.roomId).toBe(1);
    expect(response.unreadCount).toBe(3);
  });
});

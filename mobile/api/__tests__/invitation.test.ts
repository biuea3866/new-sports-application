/**
 * 게스트 초대 REST 함수 — api/invitation.ts
 *
 * - inviteGuest 호출 시 계약대로 요청 본문({inviteeUserId,canSpeak,expiresInDays})을 전송한다
 * - acceptInvitation 호출 시 accept 엔드포인트를 호출하고 수락된 초대를 반환한다
 * - rejectInvitation 호출 시 reject 엔드포인트를 호출하고 거절된 초대를 반환한다
 * - listMyInvitations 호출 시 수신함 목록을 반환한다
 * - 이미 종료된 초대 수락 시 서버 거부(409)가 그대로 전파된다
 */
import MockAdapter from 'axios-mock-adapter';
import { createBeClient } from '../be-client';
import type { InvitationResponse } from '../chat-types';

// be-client singleton 대신 테스트용 인스턴스를 주입하기 위해 모듈을 mock 처리
jest.mock('../be-client', () => {
  const actual = jest.requireActual<typeof import('../be-client')>('../be-client');
  return {
    ...actual,
    getBeClient: jest.fn(),
  };
});

import { getBeClient } from '../be-client';
import { acceptInvitation, inviteGuest, listMyInvitations, rejectInvitation } from '../invitation';

const mockedGetBeClient = jest.mocked(getBeClient);

const mockInvitation: InvitationResponse = {
  id: 10,
  roomId: 1,
  inviterUserId: 42,
  inviteeUserId: 7,
  status: 'PENDING',
  canSpeak: false,
  expiresAt: '2026-07-11T00:00:00+09:00',
  createdAt: '2026-07-04T00:00:00+09:00',
};

describe('invitation API', () => {
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

  describe('inviteGuest', () => {
    it('POST /rooms/1/invitations 호출 시 계약대로 요청 본문을 전송한다', async () => {
      mockAdapter.onPost('/rooms/1/invitations').reply(201, mockInvitation);

      const result = await inviteGuest(1, { inviteeUserId: 7, canSpeak: false, expiresInDays: 7 });

      expect(result).toEqual(mockInvitation);
      expect(JSON.parse(mockAdapter.history.post[0].data)).toEqual({
        inviteeUserId: 7,
        canSpeak: false,
        expiresInDays: 7,
      });
    });
  });

  describe('acceptInvitation', () => {
    it('POST /rooms/invitations/10/accept 호출 시 수락된 초대를 반환한다', async () => {
      const accepted: InvitationResponse = { ...mockInvitation, status: 'ACCEPTED' };
      mockAdapter.onPost('/rooms/invitations/10/accept').reply(200, accepted);

      const result = await acceptInvitation(10);

      expect(result.status).toBe('ACCEPTED');
      expect(result.roomId).toBe(1);
    });

    it('이미 종료된 초대 수락 시 서버 거부(409)가 그대로 전파된다', async () => {
      mockAdapter
        .onPost('/rooms/invitations/10/accept')
        .reply(409, { message: 'Invitation already ACCEPTED' });

      await expect(acceptInvitation(10)).rejects.toThrow();
    });
  });

  describe('rejectInvitation', () => {
    it('POST /rooms/invitations/10/reject 호출 시 거절된 초대를 반환한다', async () => {
      const rejected: InvitationResponse = { ...mockInvitation, status: 'REJECTED' };
      mockAdapter.onPost('/rooms/invitations/10/reject').reply(200, rejected);

      const result = await rejectInvitation(10);

      expect(result.status).toBe('REJECTED');
    });
  });

  describe('listMyInvitations', () => {
    it('GET /rooms/invitations/me 호출 시 수신함 목록을 반환한다', async () => {
      mockAdapter.onGet('/rooms/invitations/me').reply(200, [mockInvitation]);

      const result = await listMyInvitations();

      expect(result).toHaveLength(1);
      expect(result[0].id).toBe(10);
    });
  });
});

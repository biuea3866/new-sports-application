/**
 * useInvitations — 게스트 초대 Query 훅 (useInviteGuest/useMyInvitations/useAcceptInvitation/useRejectInvitation)
 *
 * - useInviteGuest가 {inviteeUserId,canSpeak,expiresInDays}를 계약대로 전송한다
 * - 동일 (roomId, inviteeUserId) 재초대 시 기존 PENDING 초대를 멱등하게 반환한다
 * - useAcceptInvitation 성공 시 방목록·수신함 캐시가 무효화된다
 * - useRejectInvitation 성공 시 수신함에서 해당 초대가 제거된다
 * - 이미 종료된(EXPIRED/REVOKED) 초대 수락 시 서버 거부를 전파한다
 */
import { createElement } from 'react';
import { act } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react-native';

import {
  MY_INVITATIONS_QUERY_KEY,
  useAcceptInvitation,
  useInviteGuest,
  useMyInvitations,
  useRejectInvitation,
} from '../useInvitations';
import { MY_ROOMS_QUERY_KEY } from '../useRooms';
import type { InvitationResponse } from '../../api/chat-types';

jest.mock('../../api/invitation', () => ({
  inviteGuest: jest.fn(),
  acceptInvitation: jest.fn(),
  rejectInvitation: jest.fn(),
  listMyInvitations: jest.fn(),
}));

import {
  acceptInvitation,
  inviteGuest,
  listMyInvitations,
  rejectInvitation,
} from '../../api/invitation';

const inviteGuestMock = inviteGuest as jest.MockedFunction<typeof inviteGuest>;
const acceptInvitationMock = acceptInvitation as jest.MockedFunction<typeof acceptInvitation>;
const rejectInvitationMock = rejectInvitation as jest.MockedFunction<typeof rejectInvitation>;
const listMyInvitationsMock = listMyInvitations as jest.MockedFunction<typeof listMyInvitations>;

const pendingInvitation: InvitationResponse = {
  id: 10,
  roomId: 1,
  inviterUserId: 42,
  inviteeUserId: 7,
  status: 'PENDING',
  canSpeak: false,
  expiresAt: '2026-07-11T00:00:00+09:00',
  createdAt: '2026-07-04T00:00:00+09:00',
};

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  return { wrapper, queryClient };
}

describe('useInvitations', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('useInviteGuest', () => {
    it('{inviteeUserId,canSpeak,expiresInDays}를 계약대로 전송한다', async () => {
      inviteGuestMock.mockResolvedValue(pendingInvitation);
      const { wrapper } = createWrapper();

      const { result } = renderHook(() => useInviteGuest(), { wrapper });

      await act(async () => {
        await result.current.mutateAsync({
          roomId: 1,
          request: { inviteeUserId: 7, canSpeak: false, expiresInDays: 7 },
        });
      });

      expect(inviteGuestMock).toHaveBeenCalledWith(1, {
        inviteeUserId: 7,
        canSpeak: false,
        expiresInDays: 7,
      });
      await waitFor(() => expect(result.current.data).toEqual(pendingInvitation));
    });

    it('동일 (roomId, inviteeUserId) 재초대 시 기존 PENDING 초대를 멱등하게 반환한다', async () => {
      inviteGuestMock.mockResolvedValue(pendingInvitation);
      const { wrapper } = createWrapper();

      const { result } = renderHook(() => useInviteGuest(), { wrapper });

      await act(async () => {
        await result.current.mutateAsync({
          roomId: 1,
          request: { inviteeUserId: 7, canSpeak: false, expiresInDays: 7 },
        });
      });
      await waitFor(() => expect(result.current.data).toEqual(pendingInvitation));
      const firstResult = result.current.data;

      await act(async () => {
        await result.current.mutateAsync({
          roomId: 1,
          request: { inviteeUserId: 7, canSpeak: false, expiresInDays: 7 },
        });
      });

      await waitFor(() => expect(result.current.data).toEqual(firstResult));
      expect(result.current.data?.id).toBe(pendingInvitation.id);
      expect(result.current.data?.status).toBe('PENDING');
    });
  });

  describe('useAcceptInvitation', () => {
    it('성공 시 방목록·수신함 캐시가 무효화된다', async () => {
      const accepted: InvitationResponse = { ...pendingInvitation, status: 'ACCEPTED' };
      acceptInvitationMock.mockResolvedValue(accepted);
      const { wrapper, queryClient } = createWrapper();
      const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

      const { result } = renderHook(() => useAcceptInvitation(), { wrapper });

      await act(async () => {
        await result.current.mutateAsync(10);
      });

      expect(acceptInvitationMock).toHaveBeenCalledWith(10);
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: MY_ROOMS_QUERY_KEY });
      expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: MY_INVITATIONS_QUERY_KEY });
    });

    it('이미 종료된(EXPIRED/REVOKED) 초대 수락 시 서버 거부를 전파한다', async () => {
      acceptInvitationMock.mockRejectedValue(new Error('Invitation already REVOKED'));
      const { wrapper } = createWrapper();

      const { result } = renderHook(() => useAcceptInvitation(), { wrapper });

      await act(async () => {
        await expect(result.current.mutateAsync(10)).rejects.toThrow('Invitation already REVOKED');
      });

      await waitFor(() => expect(result.current.isError).toBe(true));
    });
  });

  describe('useRejectInvitation', () => {
    it('성공 시 수신함에서 해당 초대가 제거된다', async () => {
      listMyInvitationsMock.mockResolvedValueOnce([pendingInvitation]);
      const { wrapper } = createWrapper();

      const { result: inboxResult } = renderHook(() => useMyInvitations(), { wrapper });
      await waitFor(() => expect(inboxResult.current.data).toEqual([pendingInvitation]));

      const rejected: InvitationResponse = { ...pendingInvitation, status: 'REJECTED' };
      rejectInvitationMock.mockResolvedValue(rejected);
      listMyInvitationsMock.mockResolvedValueOnce([]);

      const { result: rejectResult } = renderHook(() => useRejectInvitation(), { wrapper });
      await act(async () => {
        await rejectResult.current.mutateAsync(10);
      });

      expect(rejectInvitationMock).toHaveBeenCalledWith(10);
      await waitFor(() => expect(inboxResult.current.data).toEqual([]));
    });
  });
});

/**
 * useInvitations.ts — 게스트 초대 TanStack Query 훅 (FE-08)
 *
 * - useInviteGuest: POST /rooms/{roomId}/invitations mutation. BE가 멱등 처리(기존 PENDING
 *   초대 반환)하므로 훅은 응답을 그대로 노출한다.
 * - useMyInvitations: GET /rooms/invitations/me 조회.
 * - useAcceptInvitation: POST /rooms/invitations/{id}/accept mutation. 성공 시 방목록(MY_ROOMS_QUERY_KEY)·
 *   수신함(MY_INVITATIONS_QUERY_KEY) 캐시를 무효화한다. 응답의 roomId가 화면의 방 이동 신호로 쓰인다
 *   (호출부가 mutateAsync 결과의 roomId로 router.push 처리 — 훅은 라우팅하지 않는다).
 * - useRejectInvitation: POST /rooms/invitations/{id}/reject mutation. 성공 시 수신함 캐시를 무효화한다.
 *
 * 근거: `20260704-채팅시스템고도화-design-fe-app.md` "API 연동 표"·S7 화면 상태 표.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  acceptInvitation,
  inviteGuest,
  listMyInvitations,
  rejectInvitation,
} from '../api/invitation';
import { MY_ROOMS_QUERY_KEY } from './useRooms';
import type { InvitationResponse, InviteGuestRequest } from '../api/chat-types';

export const MY_INVITATIONS_QUERY_KEY = ['invitations', 'me'] as const;

export function useMyInvitations() {
  return useQuery<InvitationResponse[], Error>({
    queryKey: MY_INVITATIONS_QUERY_KEY,
    queryFn: () => listMyInvitations(),
  });
}

interface InviteGuestVariables {
  roomId: number;
  request: InviteGuestRequest;
}

export function useInviteGuest() {
  return useMutation<InvitationResponse, Error, InviteGuestVariables>({
    mutationFn: ({ roomId, request }) => inviteGuest(roomId, request),
  });
}

export function useAcceptInvitation() {
  const queryClient = useQueryClient();

  return useMutation<InvitationResponse, Error, number>({
    mutationFn: (id) => acceptInvitation(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: MY_ROOMS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: MY_INVITATIONS_QUERY_KEY });
    },
  });
}

export function useRejectInvitation() {
  const queryClient = useQueryClient();

  return useMutation<InvitationResponse, Error, number>({
    mutationFn: (id) => rejectInvitation(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: MY_INVITATIONS_QUERY_KEY });
    },
  });
}

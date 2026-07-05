/**
 * invitation.ts — 게스트 초대 REST 함수 (FE-08)
 *
 * 근거: `20260704-채팅시스템고도화-tdd.md` "REST API 계약"·"응답 DTO 필드 스키마"(InvitationResponse)·
 *       상태 전이 표(RoomInvitation), `20260704-채팅시스템고도화-design-fe-app.md` "API 연동 표".
 *
 * 멱등: `POST /rooms/{roomId}/invitations`는 동일 (roomId, inviteeUserId)에 이미 PENDING
 * 초대가 있으면 BE가 신규 생성 대신 기존 초대를 그대로 응답한다 — FE는 응답을 그대로 반환한다.
 */
import { getBeClient } from './be-client';
import type { InvitationResponse, InviteGuestRequest } from './chat-types';

export async function inviteGuest(
  roomId: number,
  request: InviteGuestRequest
): Promise<InvitationResponse> {
  const res = await getBeClient().post<InvitationResponse>(`/rooms/${roomId}/invitations`, request);
  return res.data;
}

export async function acceptInvitation(id: number): Promise<InvitationResponse> {
  const res = await getBeClient().post<InvitationResponse>(`/rooms/invitations/${id}/accept`);
  return res.data;
}

export async function rejectInvitation(id: number): Promise<InvitationResponse> {
  const res = await getBeClient().post<InvitationResponse>(`/rooms/invitations/${id}/reject`);
  return res.data;
}

/**
 * GET /rooms/invitations/me — 내가 받은 PENDING 초대 수신함.
 * BE 확정 대기(역제안): TDD 2026-07-04 항목에서 반영 예정이며 아직 BE 구현이 확정되지 않았다.
 * 엔드포인트가 변경되면 이 함수만 갱신하면 된다.
 */
export async function listMyInvitations(): Promise<InvitationResponse[]> {
  const res = await getBeClient().get<InvitationResponse[]>('/rooms/invitations/me');
  return res.data;
}

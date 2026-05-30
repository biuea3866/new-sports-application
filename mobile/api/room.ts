/**
 * room.ts — 채팅방 API 함수
 */
import { getBeClient } from './be-client';
import type { ListMessagesResponse, MessageResponse, RoomResponse, SendMessageRequest } from './types';

export async function listMyRooms(keyword?: string): Promise<RoomResponse[]> {
  const res = await getBeClient().get<RoomResponse[]>('/rooms/me', {
    params: keyword ? { keyword } : undefined,
  });
  return res.data;
}

export async function listMessages(
  roomId: number,
  cursor?: string
): Promise<ListMessagesResponse> {
  const res = await getBeClient().get<ListMessagesResponse>(`/rooms/${roomId}/messages`, {
    params: cursor ? { cursor } : undefined,
  });
  return res.data;
}

export async function sendMessage(
  roomId: number,
  body: SendMessageRequest
): Promise<MessageResponse> {
  const res = await getBeClient().post<MessageResponse>(`/rooms/${roomId}/messages`, body);
  return res.data;
}

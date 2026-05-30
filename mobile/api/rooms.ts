/**
 * rooms.ts — 채팅방 도메인 API 함수
 *
 * BE 경로:
 *   POST   /rooms              — 채팅방 생성
 *   GET    /rooms              — 내 채팅방 목록
 *   DELETE /rooms/{id}         — 채팅방 삭제(나가기)
 *   GET    /rooms/{id}/messages — 메시지 목록
 *   POST   /rooms/{id}/messages — 메시지 전송
 */
import { getBeClient } from './be-client';
import { PATHS } from './paths';
import { type PageResponse } from './facilities';

// ─── DTO 타입 ────────────────────────────────────────────────────────────────

export interface CreateRoomRequest {
  participantIds: number[];
  name?: string;
}

export interface RoomDto {
  id: number;
  name: string | null;
  participantCount: number;
  lastMessage: string | null;
  lastMessageAt: string | null;
  unreadCount: number;
  createdAt: string;
}

export interface MessageDto {
  id: number;
  roomId: number;
  senderId: number;
  senderName: string;
  content: string;
  createdAt: string;
}

export interface SendMessageRequest {
  content: string;
}

export interface MessageListParams {
  before?: string; // cursor (ISO datetime)
  size?: number;
}

// ─── API 함수 ────────────────────────────────────────────────────────────────

export async function createRoom(request: CreateRoomRequest): Promise<RoomDto> {
  const response = await getBeClient().post<RoomDto>(PATHS.rooms, request);
  return response.data;
}

export async function getMyRooms(): Promise<RoomDto[]> {
  const response = await getBeClient().get<RoomDto[]>(PATHS.rooms);
  return response.data;
}

export async function deleteRoom(id: number): Promise<void> {
  await getBeClient().delete(PATHS.roomById(id));
}

export async function getRoomMessages(
  id: number,
  params?: MessageListParams
): Promise<PageResponse<MessageDto>> {
  const response = await getBeClient().get<PageResponse<MessageDto>>(PATHS.roomMessages(id), {
    params,
  });
  return response.data;
}

export async function sendMessage(id: number, request: SendMessageRequest): Promise<MessageDto> {
  const response = await getBeClient().post<MessageDto>(PATHS.roomMessages(id), request);
  return response.data;
}

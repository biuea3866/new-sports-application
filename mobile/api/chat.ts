/**
 * chat.ts — 채팅 REST 엔드포인트 함수 (FE-05)
 *
 * 근거: `20260704-채팅시스템고도화-tdd.md` "REST API 계약"·"응답 DTO 필드 스키마",
 *       `20260704-채팅시스템고도화-design-fe-app.md` "API 연동 표".
 *
 * 방목록·메시지 조회/전송(GET /rooms/me, GET/POST /rooms/{id}/messages)은 기존
 * `api/room.ts`를 재사용한다 — 이 파일은 읽음/안읽은 수/backfill/goods 채팅 진입/
 * 게스트 방출 전용 신규 엔드포인트만 추가한다.
 *
 * 1·2단계 경계:
 * - getUnreadCounts / markRead: 1단계(FR-7/9, P0)
 * - backfillMessages: 2단계(FR-10). `chat.realtime.enabled` 게이팅은 호출부(FE-06)가 담당
 * - startGoodsChat: 2단계(FR-18). `chat.goods.enabled` 게이팅은 호출부(FE-10)가 담당
 * - evictGuest: 2단계(FR-15). `chat.community.enabled` 게이팅은 호출부(FE-14)가 담당
 */
import { getBeClient } from './be-client';
import type { MarkReadRequest, RoomUnreadResponse } from './chat-types';
import type { MessageResponse, RoomResponse } from './types';

/** GET /rooms/me/unread — 방별 안읽은 메시지 수 목록 */
export async function getUnreadCounts(): Promise<RoomUnreadResponse[]> {
  const res = await getBeClient().get<RoomUnreadResponse[]>('/rooms/me/unread');
  return res.data;
}

/** POST /rooms/{roomId}/read — 마지막으로 읽은 메시지 id를 서버에 반영 */
export async function markRead(
  roomId: number,
  lastReadMessageId: number
): Promise<RoomUnreadResponse> {
  const body: MarkReadRequest = { lastReadMessageId };
  const res = await getBeClient().post<RoomUnreadResponse>(`/rooms/${roomId}/read`, body);
  return res.data;
}

/**
 * GET /rooms/{roomId}/messages/backfill?afterMessageId= — 재연결 후 끊긴 구간 보정 (2단계, FR-10)
 */
export async function backfillMessages(
  roomId: number,
  afterMessageId: number
): Promise<MessageResponse[]> {
  const res = await getBeClient().get<MessageResponse[]>(`/rooms/${roomId}/messages/backfill`, {
    params: { afterMessageId },
  });
  return res.data;
}

/** POST /products/{productId}/chat — goods 상품 상세 "채팅하기" 진입 (2단계, FR-18) */
export async function startGoodsChat(productId: number): Promise<RoomResponse> {
  const res = await getBeClient().post<RoomResponse>(`/products/${productId}/chat`);
  return res.data;
}

/** POST /rooms/{roomId}/guests/{userId}/evict — 게스트 수동 방출 (2단계, FR-15) */
export async function evictGuest(roomId: number, userId: number): Promise<void> {
  await getBeClient().post<void>(`/rooms/${roomId}/guests/${userId}/evict`);
}

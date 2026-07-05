/**
 * useChat.ts — 채팅 REST TanStack Query 훅 (FE-05)
 *
 * 근거: `20260704-채팅시스템고도화-design-fe-app.md` "API 연동 표"·"상태관리 설계".
 *
 * 서버 상태 SSOT는 Query 캐시 — 안읽은 수 낙관적 +1(실시간 수신 시)은 FE-06 소켓 훅이
 * 담당하고, 이 훅들은 서버 조회/재동기화만 수행한다.
 *
 * 1·2단계 경계:
 * - useUnreadCounts / useMarkRead: 1단계(FR-7/9, P0)
 * - useBackfill: 2단계(FR-10). `chat.realtime.enabled` 게이팅은 호출부(FE-06)가 담당
 * - useStartGoodsChat: 2단계(FR-18). `chat.goods.enabled` 게이팅은 호출부(FE-10)가 담당
 * - useEvictGuest: 2단계(FR-15). `chat.community.enabled` 게이팅은 호출부(FE-14)가 담당
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  backfillMessages,
  evictGuest,
  getUnreadCounts,
  markRead,
  startGoodsChat,
} from '../api/chat';
import type { RoomUnreadResponse } from '../api/chat-types';
import type { MessageResponse, RoomResponse } from '../api/types';
import { MY_ROOMS_QUERY_KEY } from './useRooms';

export const UNREAD_COUNTS_QUERY_KEY = ['rooms', 'me', 'unread'] as const;

export function backfillQueryKey(roomId: number, afterMessageId: number) {
  return ['rooms', roomId, 'messages', 'backfill', afterMessageId] as const;
}

/** GET /rooms/me/unread — 방별 안읽은 수 조회 */
export function useUnreadCounts() {
  return useQuery<RoomUnreadResponse[], Error>({
    queryKey: UNREAD_COUNTS_QUERY_KEY,
    queryFn: () => getUnreadCounts(),
  });
}

interface MarkReadVariables {
  roomId: number;
  lastReadMessageId: number;
}

/** POST /rooms/{roomId}/read — 성공 시 해당 방의 unread 캐시만 0으로 갱신(비차단) */
export function useMarkRead() {
  const queryClient = useQueryClient();

  return useMutation<RoomUnreadResponse, Error, MarkReadVariables>({
    mutationFn: ({ roomId, lastReadMessageId }) => markRead(roomId, lastReadMessageId),
    onSuccess: (_data, variables) => {
      queryClient.setQueryData<RoomUnreadResponse[]>(UNREAD_COUNTS_QUERY_KEY, (previous) => {
        if (!previous) {
          return previous;
        }
        return previous.map((item) =>
          item.roomId === variables.roomId ? { ...item, unreadCount: 0 } : item
        );
      });
    },
  });
}

/**
 * GET /rooms/{roomId}/messages/backfill — 재연결 시 끊긴 구간 보정 (2단계, FR-10)
 * 방 진입 즉시 자동 실행하지 않고(`enabled: false`), 재연결 시점에 호출부가 `refetch()`로 트리거한다.
 */
export function useBackfill(roomId: number, afterMessageId: number) {
  return useQuery<MessageResponse[], Error>({
    queryKey: backfillQueryKey(roomId, afterMessageId),
    queryFn: () => backfillMessages(roomId, afterMessageId),
    enabled: false,
  });
}

/** POST /products/{productId}/chat — goods 상품 채팅 진입 (2단계, FR-18) */
export function useStartGoodsChat() {
  const queryClient = useQueryClient();

  return useMutation<RoomResponse, Error, number>({
    mutationFn: (productId) => startGoodsChat(productId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: MY_ROOMS_QUERY_KEY });
    },
  });
}

interface EvictGuestVariables {
  roomId: number;
  userId: number;
}

/** POST /rooms/{roomId}/guests/{userId}/evict — 게스트 수동 방출 (2단계, FR-15) */
export function useEvictGuest() {
  return useMutation<void, Error, EvictGuestVariables>({
    mutationFn: ({ roomId, userId }) => evictGuest(roomId, userId),
  });
}

/**
 * useRoomListItems.ts — 채팅방 목록 화면(S1) 컨테이너 병합 로직
 *
 * 근거: FE-09 티켓, `20260704-채팅시스템고도화-design-fe-app.md` S1 상태 표.
 *
 * `useRooms`(방 목록)와 `useUnreadCounts`(안읽은 수)를 roomId로 병합해 화면이 렌더링만
 * 하도록 표시용 뷰 모델(`RoomListItemView`)을 만든다. 안읽은 수 조회 실패는 화면 전체
 * 에러로 취급하지 않고 0으로 폴백한다(design-fe-app.md "실패 시 배지 0 폴백(비차단)") —
 * loading/error 판단은 방 목록(useRooms) 조회 결과만 기준으로 한다.
 */
import { useMemo } from 'react';
import { useUnreadCounts } from './useChat';
import { useRooms } from './useRooms';
import type { RoomResponse } from '../api/types';
import type { RoomUnreadResponse } from '../api/chat-types';

export interface RoomListItemView {
  id: number;
  displayName: string;
  previewText: string | null;
  timeLabel: string | null;
  unreadCount: number;
}

export interface UseRoomListItemsResult {
  items: RoomListItemView[];
  isLoading: boolean;
  isError: boolean;
  isRefreshing: boolean;
  refetch: () => void;
}

function resolveDisplayName(room: RoomResponse): string {
  if (room.name && room.name.trim().length > 0) {
    return room.name;
  }
  return room.type === 'DIRECT' ? '1:1 채팅' : '그룹 채팅';
}

function formatTimeLabel(lastMessageAt: string | null | undefined): string | null {
  if (!lastMessageAt) {
    return null;
  }
  const parsedDate = new Date(lastMessageAt);
  if (Number.isNaN(parsedDate.getTime())) {
    return null;
  }
  const hours = String(parsedDate.getHours()).padStart(2, '0');
  const minutes = String(parsedDate.getMinutes()).padStart(2, '0');
  return `${hours}:${minutes}`;
}

function resolveUnreadCount(roomId: number, unreadCounts: RoomUnreadResponse[]): number {
  const matched = unreadCounts.find((unreadCount) => unreadCount.roomId === roomId);
  return matched?.unreadCount ?? 0;
}

export function mapRoomsToListItems(
  rooms: RoomResponse[],
  unreadCounts: RoomUnreadResponse[]
): RoomListItemView[] {
  return rooms.map((room) => ({
    id: room.id,
    displayName: resolveDisplayName(room),
    previewText: room.lastMessagePreview ?? null,
    timeLabel: formatTimeLabel(room.lastMessageAt),
    unreadCount: resolveUnreadCount(room.id, unreadCounts),
  }));
}

export function useRoomListItems(): UseRoomListItemsResult {
  const roomsQuery = useRooms();
  const unreadQuery = useUnreadCounts();

  const items = useMemo(
    () => mapRoomsToListItems(roomsQuery.data ?? [], unreadQuery.data ?? []),
    [roomsQuery.data, unreadQuery.data]
  );

  return {
    items,
    isLoading: roomsQuery.isLoading,
    isError: roomsQuery.isError,
    isRefreshing: roomsQuery.isRefetching,
    refetch: () => {
      void roomsQuery.refetch();
      void unreadQuery.refetch();
    },
  };
}

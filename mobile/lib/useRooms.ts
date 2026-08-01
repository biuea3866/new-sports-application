/**
 * useRooms — GET /rooms/me TanStack Query 훅
 * useMessages — GET /rooms/{id}/messages TanStack Query 훅
 * useSendMessage — POST /rooms/{id}/messages mutation 훅
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getRoom, listMessages, listMyRooms, sendMessage } from '../api/room';
import type { ListMessagesResponse, MessageResponse, RoomResponse } from '../api/types';

export const MY_ROOMS_QUERY_KEY = ['rooms', 'me'] as const;

export function messagesQueryKey(roomId: number) {
  return ['rooms', roomId, 'messages'] as const;
}

export function roomQueryKey(roomId: number) {
  return ['rooms', roomId] as const;
}

/** 채팅방 단건 조회 — 대화 화면 헤더에 방 이름을 표시하기 위해 사용한다. */
export function useRoom(roomId: number) {
  return useQuery<RoomResponse, Error>({
    queryKey: roomQueryKey(roomId),
    queryFn: () => getRoom(roomId),
    enabled: roomId > 0,
  });
}

export function useRooms() {
  return useQuery<RoomResponse[], Error>({
    queryKey: MY_ROOMS_QUERY_KEY,
    queryFn: () => listMyRooms(),
  });
}

export function useMessages(roomId: number) {
  return useQuery<ListMessagesResponse, Error>({
    queryKey: messagesQueryKey(roomId),
    queryFn: () => listMessages(roomId),
    enabled: roomId > 0,
  });
}

interface SendMessageVariables {
  roomId: number;
  content: string;
}

export function useSendMessage() {
  const queryClient = useQueryClient();

  return useMutation<MessageResponse, Error, SendMessageVariables>({
    mutationFn: ({ roomId, content }) => sendMessage(roomId, { content }),
    onSuccess: (_data, variables) => {
      void queryClient.invalidateQueries({ queryKey: messagesQueryKey(variables.roomId) });
      void queryClient.invalidateQueries({ queryKey: MY_ROOMS_QUERY_KEY });
    },
  });
}

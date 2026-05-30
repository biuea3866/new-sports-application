/**
 * useRooms — GET /rooms/me TanStack Query 훅
 * useMessages — GET /rooms/{id}/messages TanStack Query 훅
 * useSendMessage — POST /rooms/{id}/messages mutation 훅
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { listMessages, listMyRooms, sendMessage } from '../api/room';
import type { ListMessagesResponse, MessageResponse, RoomResponse } from '../api/types';

export const MY_ROOMS_QUERY_KEY = ['rooms', 'me'] as const;

export function messagesQueryKey(roomId: number) {
  return ['rooms', roomId, 'messages'] as const;
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

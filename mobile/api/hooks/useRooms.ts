/**
 * useRooms.ts — 채팅방 도메인 react-query 훅
 */
import {
  useQuery,
  useMutation,
  useQueryClient,
  type UseQueryOptions,
  type UseMutationOptions,
} from '@tanstack/react-query';
import {
  createRoom,
  getMyRooms,
  deleteRoom,
  getRoomMessages,
  sendMessage,
  type CreateRoomRequest,
  type RoomDto,
  type MessageDto,
  type SendMessageRequest,
  type MessageListParams,
} from '../rooms';
import { type PageResponse } from '../facilities';
import { roomsKeys } from '../queryKeys';

export function useMyRoomsQuery(
  options?: Omit<UseQueryOptions<RoomDto[]>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: roomsKeys.mine(),
    queryFn: getMyRooms,
    ...options,
  });
}

export function useRoomMessagesQuery(
  roomId: number,
  params?: MessageListParams,
  options?: Omit<UseQueryOptions<PageResponse<MessageDto>>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: roomsKeys.messages(roomId),
    queryFn: () => getRoomMessages(roomId, params),
    enabled: roomId > 0,
    ...options,
  });
}

export function useCreateRoomMutation(
  options?: UseMutationOptions<RoomDto, Error, CreateRoomRequest>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createRoom,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: roomsKeys.mine() });
    },
    ...options,
  });
}

export function useDeleteRoomMutation(
  options?: UseMutationOptions<void, Error, number>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deleteRoom(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: roomsKeys.mine() });
    },
    ...options,
  });
}

export function useSendMessageMutation(
  roomId: number,
  options?: UseMutationOptions<MessageDto, Error, SendMessageRequest>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: SendMessageRequest) => sendMessage(roomId, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: roomsKeys.messages(roomId) });
    },
    ...options,
  });
}

/**
 * useNotifications.ts — 알림 도메인 react-query 훅
 */
import {
  useQuery,
  useMutation,
  useQueryClient,
  type UseQueryOptions,
  type UseMutationOptions,
} from '@tanstack/react-query';
import {
  getMyNotifications,
  getUnreadNotificationCount,
  markNotificationAsRead,
  type NotificationDto,
  type NotificationListParams,
  type UnreadCountDto,
} from '../notifications';
import { type PageResponse } from '../facilities';
import { notificationsKeys } from '../queryKeys';

export function useMyNotificationsQuery(
  params?: NotificationListParams,
  options?: Omit<UseQueryOptions<PageResponse<NotificationDto>>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: notificationsKeys.myList(params ?? {}),
    queryFn: () => getMyNotifications(params),
    ...options,
  });
}

export function useUnreadNotificationCountQuery(
  options?: Omit<UseQueryOptions<UnreadCountDto>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: notificationsKeys.unreadCount(),
    queryFn: getUnreadNotificationCount,
    ...options,
  });
}

export function useMarkNotificationAsReadMutation(
  options?: UseMutationOptions<NotificationDto, Error, number>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => markNotificationAsRead(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: notificationsKeys.mine() });
      queryClient.invalidateQueries({ queryKey: notificationsKeys.unreadCount() });
    },
    ...options,
  });
}

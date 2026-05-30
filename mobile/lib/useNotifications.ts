/**
 * useNotifications — GET /notifications/me TanStack Query 훅
 * useUnreadCount — GET /notifications/me/unread-count 훅
 * useMarkNotificationRead — PATCH /notifications/{id}/read mutation 훅
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getMyNotifications,
  getUnreadCount,
  markNotificationRead,
} from '../api/notifications';
import type { NotificationListResponse, UnreadCountResponse } from '../api/types';

export const NOTIFICATIONS_QUERY_KEY = ['notifications', 'me'] as const;
export const UNREAD_COUNT_QUERY_KEY = ['notifications', 'unread-count'] as const;

export function useNotifications(page = 0, size = 20) {
  return useQuery<NotificationListResponse, Error>({
    queryKey: [...NOTIFICATIONS_QUERY_KEY, page, size],
    queryFn: () => getMyNotifications(page, size),
  });
}

export function useUnreadCount() {
  return useQuery<UnreadCountResponse, Error>({
    queryKey: UNREAD_COUNT_QUERY_KEY,
    queryFn: getUnreadCount,
  });
}

export function useMarkNotificationRead() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, number>({
    mutationFn: (id) => markNotificationRead(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_QUERY_KEY });
      void queryClient.invalidateQueries({ queryKey: UNREAD_COUNT_QUERY_KEY });
    },
  });
}

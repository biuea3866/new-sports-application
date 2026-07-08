/**
 * notifications.ts — 알림 API 함수
 */
import { getBeClient } from './be-client';
import type { NotificationListResponse, UnreadCountResponse } from './types';

export async function getMyNotifications(
  page = 0,
  size = 20
): Promise<NotificationListResponse> {
  const res = await getBeClient().get<NotificationListResponse>('/notifications/me', {
    params: { page, size },
  });
  return res.data;
}

export async function getUnreadCount(): Promise<UnreadCountResponse> {
  const res = await getBeClient().get<UnreadCountResponse>('/notifications/me/unread-count');
  return res.data;
}

export async function markNotificationRead(id: number): Promise<void> {
  await getBeClient().patch(`/notifications/${id}/read`);
}

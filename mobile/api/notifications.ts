/**
 * notifications.ts — 알림 도메인 API 함수
 *
 * BE 경로:
 *   GET   /notifications/me               — 내 알림 목록
 *   GET   /notifications/me/unread-count  — 미읽음 수
 *   PATCH /notifications/{id}/read        — 알림 읽음 처리
 */
import { getBeClient } from './be-client';
import { PATHS } from './paths';
import { type PageResponse } from './facilities';

// ─── DTO 타입 ────────────────────────────────────────────────────────────────

export type NotificationType =
  | 'BOOKING_CONFIRMED'
  | 'BOOKING_CANCELLED'
  | 'ORDER_STATUS_CHANGED'
  | 'EVENT_REMINDER'
  | 'SYSTEM';

export interface NotificationDto {
  id: number;
  type: NotificationType;
  title: string;
  body: string;
  read: boolean;
  createdAt: string;
}

export interface UnreadCountDto {
  count: number;
}

export interface NotificationListParams {
  page?: number;
  size?: number;
}

// ─── API 함수 ────────────────────────────────────────────────────────────────

export async function getMyNotifications(
  params?: NotificationListParams
): Promise<PageResponse<NotificationDto>> {
  const response = await getBeClient().get<PageResponse<NotificationDto>>(PATHS.notificationsMe, {
    params,
  });
  return response.data;
}

export async function getUnreadNotificationCount(): Promise<UnreadCountDto> {
  const response = await getBeClient().get<UnreadCountDto>(PATHS.notificationsUnreadCount);
  return response.data;
}

export async function markNotificationAsRead(id: number): Promise<NotificationDto> {
  const response = await getBeClient().patch<NotificationDto>(PATHS.notificationRead(id));
  return response.data;
}

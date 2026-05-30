/**
 * 알림 관련 타입 정의 및 BFF API 클라이언트.
 * Client Component에서는 /api/portal/notifications BFF 엔드포인트만 호출한다.
 */

import type { Notification, NotificationPage } from "./types";

export type { Notification, NotificationPage };

export interface ListNotificationsParams {
  onlyUnread?: boolean;
  page?: number;
  size?: number;
}

/** 내 알림 목록 조회 */
export async function fetchMyNotifications(
  params: ListNotificationsParams = {}
): Promise<NotificationPage> {
  const query = new URLSearchParams();
  if (params.onlyUnread !== undefined)
    query.set("onlyUnread", String(params.onlyUnread));
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));
  const qs = query.toString();
  const url = qs
    ? `/api/portal/notifications?${qs}`
    : "/api/portal/notifications";

  const res = await fetch(url, { cache: "no-store" });
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as {
      message?: string;
    } | null;
    throw new Error(body?.message ?? `알림 목록 조회 실패: ${res.status}`);
  }
  return res.json() as Promise<NotificationPage>;
}

/** 알림 읽음 처리 */
export async function markNotificationRead(
  notificationId: number
): Promise<Notification> {
  const res = await fetch(
    `/api/portal/notifications/${notificationId}/read`,
    { method: "PATCH", cache: "no-store" }
  );
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as {
      message?: string;
    } | null;
    throw new Error(body?.message ?? `알림 읽음 처리 실패: ${res.status}`);
  }
  return res.json() as Promise<Notification>;
}

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

/**
 * 알림 읽음 처리.
 *
 * BE `PATCH /notifications/{id}/read` 응답은 목록(`MyNotificationResponse`)과 형태가 다른
 * 발송 원형(`NotificationResponse`)이다. 호출부는 성공 후 목록을 다시 불러오므로 본문을
 * 쓰지 않는다 — 목록 타입으로 단언해 계약을 왜곡하지 않고 반환하지 않는다.
 */
export async function markNotificationRead(notificationId: number): Promise<void> {
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
}

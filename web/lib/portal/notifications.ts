/**
 * 알림 관련 타입 정의 및 BFF API 클라이언트.
 * Client Component에서는 /api/portal/notifications BFF 엔드포인트만 호출한다.
 */

import type { Notification, NotificationPage } from "./types";
import { NotificationPageSchema } from "./schemas";

export type { Notification, NotificationPage };

export interface ListNotificationsParams {
  onlyUnread?: boolean;
  page?: number;
  size?: number;
}

/**
 * 내 알림 목록 조회.
 *
 * 응답을 **실제로 파싱한다**. 캐스팅만 하면 스키마가 런타임에 쓰이지 않아 계약 위반을 못 잡는다 —
 * BE가 발송 원형에서 사용자 관점 응답(`MyNotificationResponse`)으로 바꿨을 때 전 필드가 조용히
 * `undefined`가 되어 카드가 통째로 비었고(02-파트너포털/17 캡쳐), 타입·테스트는 전부 GREEN이었다.
 * 형제 경로(`fetchPartnerSales`)와 같은 기준으로 파싱해 조용히 틀리는 대신 즉시 실패하게 한다.
 */
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
  return NotificationPageSchema.parse(await res.json());
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

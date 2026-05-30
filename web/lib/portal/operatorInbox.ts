/**
 * 운영자 알림센터 타입 정의 및 BFF API 클라이언트.
 * Client Component에서는 /api/operator/inbox BFF 엔드포인트만 호출한다.
 */

export type InboxNotificationType =
  | "ANOMALY"
  | "LOW_INVENTORY"
  | "BOOKING_CONFLICT"
  | "POLICY_VIOLATION"
  | "AUTOMATION_FAILURE";

export type InboxNotificationStatus = "UNREAD" | "READ" | "ARCHIVED";

export interface InboxNotification {
  id: number;
  recipientUserId: number;
  type: InboxNotificationType;
  title: string;
  body: string;
  link: string | null;
  status: InboxNotificationStatus;
  readAt: string | null;
  createdAt: string;
}

export interface ListInboxParams {
  type?: InboxNotificationType;
  status?: InboxNotificationStatus;
  page?: number;
  size?: number;
}

export interface ListInboxResponse {
  content: InboxNotification[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface UnreadCountResponse {
  count: number;
}

/** 알림 목록 조회 */
export async function fetchInbox(params: ListInboxParams = {}): Promise<ListInboxResponse> {
  const query = new URLSearchParams();
  if (params.type !== undefined) query.set("type", params.type);
  if (params.status !== undefined) query.set("status", params.status);
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));
  const qs = query.toString();
  const url = qs ? `/api/operator/inbox?${qs}` : "/api/operator/inbox";

  const res = await fetch(url, { cache: "no-store" });
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `알림 목록 조회 실패: ${res.status}`);
  }
  return res.json() as Promise<ListInboxResponse>;
}

/** 읽지 않은 알림 수 조회 */
export async function fetchUnreadCount(): Promise<UnreadCountResponse> {
  const res = await fetch("/api/operator/inbox/unread-count", { cache: "no-store" });
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `읽지 않은 알림 수 조회 실패: ${res.status}`);
  }
  return res.json() as Promise<UnreadCountResponse>;
}

/** 알림 읽음 처리 */
export async function markInboxRead(id: number): Promise<void> {
  const res = await fetch(`/api/operator/inbox/${id}/read`, {
    method: "PATCH",
  });
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `알림 읽음 처리 실패: ${res.status}`);
  }
}

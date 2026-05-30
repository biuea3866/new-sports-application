/**
 * 알림 발송 BFF API 클라이언트.
 * Client Component에서는 /api/portal/notifications BFF 엔드포인트만 호출한다.
 */
import type { NotificationResult, SendNotificationInput } from "./types";

/** 알림 발송 */
export async function sendNotification(
  input: SendNotificationInput
): Promise<NotificationResult> {
  const res = await fetch("/api/portal/notifications", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `알림 발송 실패: ${res.status}`);
  }
  return res.json() as Promise<NotificationResult>;
}

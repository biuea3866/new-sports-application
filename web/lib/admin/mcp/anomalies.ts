/**
 * MCP 이상 패턴 히스토리 타입 정의 및 BFF API 클라이언트.
 * Client Component에서는 /api/mcp/anomaly-events BFF 엔드포인트만 호출한다.
 */

export type AnomalyEventStatus = "OPEN" | "RESOLVED" | "FALSE_POSITIVE";

export interface McpAnomalyEventResponse {
  id: number;
  tokenId: number;
  ownerUserId: number;
  detectedAt: string;
  currentHourCount: number;
  baselineAverage: number;
  status: AnomalyEventStatus;
  falsePositive: boolean;
  resolvedAt: string | null;
  note: string | null;
}

export interface ListAnomalyEventsResponse {
  content: McpAnomalyEventResponse[];
  totalElements: number;
  totalPages: number;
  pageNumber: number;
  pageSize: number;
}

export interface FetchAnomalyEventsParams {
  page?: number;
  size?: number;
}

/** BFF `/api/mcp/anomaly-events` 엔드포인트를 통해 이상 패턴 목록을 조회한다. */
export async function fetchAnomalyEvents(
  params: FetchAnomalyEventsParams = {}
): Promise<ListAnomalyEventsResponse> {
  const query = new URLSearchParams();
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));

  const url = query.toString()
    ? `/api/mcp/anomaly-events?${query.toString()}`
    : "/api/mcp/anomaly-events";

  const res = await fetch(url, { cache: "no-store" });

  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `이상 패턴 조회 실패: ${res.status}`);
  }

  return res.json() as Promise<ListAnomalyEventsResponse>;
}

/** BFF `/api/mcp/anomaly-events/{id}/false-positive` 엔드포인트를 통해 false positive를 표시한다. */
export async function markFalsePositive(
  id: number,
  note: string
): Promise<void> {
  const res = await fetch(`/api/mcp/anomaly-events/${id}/false-positive`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ note }),
  });

  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `false positive 처리 실패: ${res.status}`);
  }
}

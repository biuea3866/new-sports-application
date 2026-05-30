/**
 * MCP 사용 분석 타입 정의 및 BFF API 클라이언트.
 * Client Component에서는 /api/admin/mcp/usage-analytics BFF 엔드포인트만 호출한다.
 */

export interface DailyStat {
  date: string;
  toolName: string;
  callCount: number;
}

export interface ToolCallStat {
  toolName: string;
  callCount: number;
}

export interface ErrorRateStat {
  totalCount: number;
  errorCount: number;
  errorRatePercent: number;
}

export interface ToolLatencyStat {
  toolName: string;
  p95LatencyMs: number;
}

export interface TokenUsageStat {
  tokenId: number;
  callCount: number;
  errorCount: number;
  errorRatePercent: number;
  lastCalledAt: string | null;
}

export interface UsageAnalyticsResponse {
  dailyStats: DailyStat[];
  toolCallStats: ToolCallStat[];
  errorRateStat: ErrorRateStat;
  toolLatencyStats: ToolLatencyStat[];
  tokenUsageStats: TokenUsageStat[];
}

export interface FetchUsageAnalyticsParams {
  from: string;
  to: string;
}

/** BFF `/api/admin/mcp/usage-analytics` 엔드포인트를 통해 사용 분석 데이터를 조회한다. */
export async function fetchUsageAnalytics(
  params: FetchUsageAnalyticsParams
): Promise<UsageAnalyticsResponse> {
  const query = new URLSearchParams({ from: params.from, to: params.to });
  const res = await fetch(`/api/admin/mcp/usage-analytics?${query.toString()}`, {
    cache: "no-store",
  });

  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `사용 분석 조회 실패: ${res.status}`);
  }

  return res.json() as Promise<UsageAnalyticsResponse>;
}

/**
 * MCP 감사 로그 타입 정의 및 BFF API 클라이언트.
 * Client Component에서는 /api/admin/mcp/audit-logs BFF 엔드포인트만 호출한다.
 */

export interface McpAuditLogResponse {
  id: number;
  tokenId: number | null;
  toolName: string;
  paramsMasked: string | null;
  statusCode: number;
  latencyMs: number;
  ipAddr: string | null;
  calledAt: string;
}

export interface ListMcpAuditLogsResponse {
  content: McpAuditLogResponse[];
  totalElements: number;
  totalPages: number;
  pageNumber: number;
  pageSize: number;
}

export interface FetchAuditLogsParams {
  from: string;
  to: string;
  page?: number;
  size?: number;
}

/** BFF `/api/admin/mcp/audit-logs` 엔드포인트를 통해 감사 로그를 조회한다. */
export async function fetchAuditLogs(
  params: FetchAuditLogsParams
): Promise<ListMcpAuditLogsResponse> {
  const query = new URLSearchParams({ from: params.from, to: params.to });
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));

  const res = await fetch(`/api/admin/mcp/audit-logs?${query.toString()}`, {
    cache: "no-store",
  });

  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `감사 로그 조회 실패: ${res.status}`);
  }

  return res.json() as Promise<ListMcpAuditLogsResponse>;
}

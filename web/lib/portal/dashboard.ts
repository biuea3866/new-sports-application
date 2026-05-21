/**
 * Dashboard B2B API 클라이언트.
 * 반드시 서버 사이드(Server Component, Route Handler)에서만 사용한다.
 */
import "server-only";
import { beClient } from "@/lib/server/be-client";
import { DashboardSummarySchema } from "./schemas";
import { throwIfErrorResponse } from "./error";
import type { DashboardSummary } from "./types";

export async function getMyDashboardSummary(): Promise<DashboardSummary> {
  const response = await beClient("/api/b2b/dashboard/summary", { method: "GET" });
  await throwIfErrorResponse(response);
  const data: unknown = await response.json();
  return DashboardSummarySchema.parse(data);
}

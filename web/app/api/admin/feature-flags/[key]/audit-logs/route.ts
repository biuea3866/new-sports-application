/**
 * BFF Route Handler — /api/admin/feature-flags/[key]/audit-logs
 * GET : 감사 로그 이력 조회 (?page=&size=) → BE GET /admin/feature-flags/{key}/audit-logs forward
 */
import { NextRequest, NextResponse } from "next/server";
import { forwardBeResponse } from "@/app/api/portal/_lib/bff-helpers";

interface RouteContext {
  params: { key: string };
}

export async function GET(
  request: NextRequest,
  { params }: RouteContext
): Promise<NextResponse> {
  const { searchParams } = request.nextUrl;
  const query = searchParams.toString();
  const basePath = `/admin/feature-flags/${params.key}/audit-logs`;
  const path = query ? `${basePath}?${query}` : basePath;

  return forwardBeResponse(path, { method: "GET" });
}

/**
 * BFF Route Handler — /api/operator/dashboard/kpi
 * GET : 운영 통합 KPI 조회 → BE GET /api/operator/dashboard/kpi forward
 *
 * 쿼리 파라미터: from, to
 */
import { NextRequest, NextResponse } from "next/server";
import { forwardBeResponse } from "@/app/api/portal/_lib/bff-helpers";
import { getSessionInfo } from "@/lib/server/auth";

export async function GET(request: NextRequest): Promise<NextResponse> {
  const session = getSessionInfo();
  if (!session) {
    return NextResponse.json({ message: "로그인이 필요합니다." }, { status: 401 });
  }

  const { searchParams } = request.nextUrl;
  const query = searchParams.toString();
  const path = query
    ? `/api/operator/dashboard/kpi?${query}`
    : "/api/operator/dashboard/kpi";
  return forwardBeResponse(path, { method: "GET" });
}

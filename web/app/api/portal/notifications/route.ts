/**
 * BFF Route Handler — /api/portal/notifications
 * GET : 내 알림 목록 조회 → BE GET /notifications/me forward
 *
 * 쿼리 파라미터: onlyUnread, page, size
 */
import { NextRequest, NextResponse } from "next/server";
import { forwardBeResponse } from "../_lib/bff-helpers";

export async function GET(request: NextRequest): Promise<NextResponse> {
  const { searchParams } = request.nextUrl;
  const query = searchParams.toString();
  const path = query
    ? `/notifications/me?${query}`
    : "/notifications/me";
  return forwardBeResponse(path, { method: "GET" });
}

/**
 * BFF Route Handler — /api/portal/users
 * GET : 유저 목록 조회 (페이징/검색/role 필터) → BE GET /admin/users forward
 */
import { NextRequest, NextResponse } from "next/server";
import { forwardBeResponse } from "../_lib/bff-helpers";

export async function GET(request: NextRequest): Promise<NextResponse> {
  const { searchParams } = request.nextUrl;
  const query = searchParams.toString();
  const path = query ? `/admin/users?${query}` : "/admin/users";
  return forwardBeResponse(path, { method: "GET" });
}

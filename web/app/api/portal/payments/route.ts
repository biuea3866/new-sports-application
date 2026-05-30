/**
 * BFF Route Handler — /api/portal/payments
 * GET : 내 결제 목록 조회 → BE GET /payments/me forward
 */
import { NextRequest, NextResponse } from "next/server";
import { forwardBeResponse } from "../_lib/bff-helpers";

export async function GET(request: NextRequest): Promise<NextResponse> {
  const { searchParams } = request.nextUrl;
  const query = searchParams.toString();
  const path = query ? `/payments/me?${query}` : "/payments/me";
  return forwardBeResponse(path, { method: "GET" });
}

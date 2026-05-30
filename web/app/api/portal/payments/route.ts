/**
 * BFF Route Handler — /api/portal/payments
 * GET : 내 결제 목록 조회 → BE GET /payments/me forward
 *
 * 쿼리 파라미터: status, paidAtFrom, paidAtTo, page, size
 */
import { type NextRequest, NextResponse } from "next/server";
import { forwardBeResponse } from "../_lib/bff-helpers";

export async function GET(request: NextRequest): Promise<NextResponse> {
  const { searchParams } = request.nextUrl;
  const qs = searchParams.toString();
  const bePath = qs ? `/payments/me?${qs}` : "/payments/me";
  return forwardBeResponse(bePath);
}

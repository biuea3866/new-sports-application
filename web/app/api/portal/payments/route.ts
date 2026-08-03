/**
 * BFF Route Handler — /api/portal/payments
 * GET : 파트너 매출 내역 조회 → BE GET /api/operator/dashboard/sales forward
 *
 * 쿼리 파라미터: status, paidAtFrom, paidAtTo, page, size
 *
 * 구매자 스코프(`/payments/me`)가 아니다 — 포털 "매출·결제 내역"은 파트너가 **판** 건을 봐야
 * 한다. 구매자 스코프를 호출하면 파트너 본인 결제만 잡혀, 실제 결제가 전부 구매자 명의인
 * 이 서비스에서는 항상 0건이 된다. 조회 대상 판매자는 BE가 인증 주체로 정한다.
 */
import { type NextRequest, NextResponse } from "next/server";
import { forwardBeResponse } from "../_lib/bff-helpers";

export async function GET(request: NextRequest): Promise<NextResponse> {
  const { searchParams } = request.nextUrl;
  const qs = searchParams.toString();
  const bePath = qs
    ? `/api/operator/dashboard/sales?${qs}`
    : "/api/operator/dashboard/sales";
  return forwardBeResponse(bePath);
}

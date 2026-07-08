import { NextRequest, NextResponse } from "next/server";
import { forwardBeResponse } from "../_lib/bff-helpers";

/**
 * 대기질 BFF 프록시.
 * BE 계약: `GET /air-quality?lat&lng` → 200 AirQualityResponse.
 * BE는 조회 실패 시에도 200 + representativeGrade="UNKNOWN"으로 응답하므로
 * 이 라우트는 별도 판단 없이 쿼리·응답을 그대로 전달한다(`forwardBeResponse` 재사용).
 * 근거 티켓: FE-06-web-airquality-bff-hook.md.
 */
export async function GET(request: NextRequest): Promise<NextResponse> {
  const { searchParams } = request.nextUrl;
  const query = searchParams.toString();
  const path = query ? `/air-quality?${query}` : "/air-quality";
  return forwardBeResponse(path, { method: "GET" });
}

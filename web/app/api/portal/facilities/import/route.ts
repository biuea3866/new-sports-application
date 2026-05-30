/**
 * BFF Route Handler — /api/portal/facilities/import
 * POST : 레거시 시설 일괄 임포트 → BE POST /admin/facilities/import forward
 */
import { NextRequest, NextResponse } from "next/server";
import { forwardBeResponse } from "../../_lib/bff-helpers";

export async function POST(request: NextRequest): Promise<NextResponse> {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ message: "요청 본문이 유효한 JSON이 아닙니다." }, { status: 400 });
  }

  return forwardBeResponse("/admin/facilities/import", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

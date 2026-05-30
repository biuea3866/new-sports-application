/**
 * BFF Route Handler — /api/portal/notifications
 * POST : 알림 발송 → BE POST /admin/notifications/send forward
 */
import { NextRequest, NextResponse } from "next/server";
import { forwardBeResponse } from "../_lib/bff-helpers";

export async function POST(request: NextRequest): Promise<NextResponse> {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ message: "요청 본문이 유효한 JSON이 아닙니다." }, { status: 400 });
  }

  return forwardBeResponse("/admin/notifications/send", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

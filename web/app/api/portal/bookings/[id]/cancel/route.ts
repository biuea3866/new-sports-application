/**
 * BFF Route Handler — /api/portal/bookings/[id]/cancel
 * POST : 예약 취소 → BE POST /bookings/{id}/cancel forward
 */
import { NextRequest, NextResponse } from "next/server";
import { forwardBeResponse } from "../../../_lib/bff-helpers";

interface RouteParams {
  params: { id: string };
}

export async function POST(
  request: NextRequest,
  { params }: RouteParams
): Promise<NextResponse> {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    body = {};
  }

  return forwardBeResponse(`/bookings/${params.id}/cancel`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

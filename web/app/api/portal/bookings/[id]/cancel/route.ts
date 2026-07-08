/**
 * BFF Route Handler — /api/portal/bookings/[id]/cancel
 * POST : 예약 취소 → BE POST /bookings/{id}/cancel forward
 */
import { NextRequest, NextResponse } from "next/server";
import { forwardBeResponse, zodValidationError } from "../../../_lib/bff-helpers";
import { CancelBookingInputSchema } from "@/lib/portal/schemas";

interface RouteContext {
  params: { id: string };
}

export async function POST(
  request: NextRequest,
  { params }: RouteContext
): Promise<NextResponse> {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    body = {};
  }

  const parsed = CancelBookingInputSchema.safeParse(body);
  if (!parsed.success) {
    return zodValidationError(parsed.error);
  }

  return forwardBeResponse(`/bookings/${params.id}/cancel`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(parsed.data),
  });
}

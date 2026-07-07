/**
 * BFF Route Handler — /api/portal/facilities/[id]/slots/[slotId]/open
 * PATCH : 슬롯 오픈(신규 예약 허용) → BE PATCH /facilities/{facilityId}/slots/{slotId}/open forward
 */
import { NextRequest, NextResponse } from "next/server";
import { forwardBeResponse } from "@/app/api/portal/_lib/bff-helpers";

interface RouteContext {
  params: { id: string; slotId: string };
}

export async function PATCH(
  _request: NextRequest,
  { params }: RouteContext
): Promise<NextResponse> {
  return forwardBeResponse(`/facilities/${params.id}/slots/${params.slotId}/open`, {
    method: "PATCH",
  });
}

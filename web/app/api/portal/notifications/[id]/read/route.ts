/**
 * BFF Route Handler — /api/portal/notifications/[id]/read
 * PATCH : 알림 읽음 처리 → BE PATCH /notifications/{id}/read forward
 */
import { NextRequest, NextResponse } from "next/server";
import { forwardBeResponse } from "../../../_lib/bff-helpers";

export async function PATCH(
  _request: NextRequest,
  { params }: { params: { id: string } }
): Promise<NextResponse> {
  const { id } = params;
  return forwardBeResponse(`/notifications/${id}/read`, { method: "PATCH" });
}

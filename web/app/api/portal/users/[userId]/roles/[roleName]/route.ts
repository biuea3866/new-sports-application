/**
 * BFF Route Handler — /api/portal/users/[userId]/roles/[roleName]
 * POST : 역할 부여 → BE POST /admin/users/{userId}/roles/{roleName} forward
 * DELETE : 역할 회수 → BE DELETE /admin/users/{userId}/roles/{roleName} forward
 */
import { NextRequest, NextResponse } from "next/server";
import { forwardBeResponse } from "../../../../_lib/bff-helpers";

interface RouteParams {
  params: { userId: string; roleName: string };
}

export async function POST(
  _request: NextRequest,
  { params }: RouteParams
): Promise<NextResponse> {
  return forwardBeResponse(`/admin/users/${params.userId}/roles/${params.roleName}`, {
    method: "POST",
  });
}

export async function DELETE(
  _request: NextRequest,
  { params }: RouteParams
): Promise<NextResponse> {
  return forwardBeResponse(`/admin/users/${params.userId}/roles/${params.roleName}`, {
    method: "DELETE",
  });
}

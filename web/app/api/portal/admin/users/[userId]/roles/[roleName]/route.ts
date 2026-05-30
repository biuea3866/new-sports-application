/**
 * BFF Route Handler — /api/portal/admin/users/[userId]/roles/[roleName]
 * POST : 역할 부여 → BE POST /admin/users/{userId}/roles/{roleName} forward
 * DELETE : 역할 회수 → BE DELETE /admin/users/{userId}/roles/{roleName} forward
 */
import { NextRequest, NextResponse } from "next/server";
import { getSessionInfo } from "@/lib/server/auth";
import { forwardBeResponse } from "../../../../../_lib/bff-helpers";

interface RouteContext {
  params: { userId: string; roleName: string };
}

export async function POST(
  _request: NextRequest,
  { params }: RouteContext
): Promise<NextResponse> {
  const session = getSessionInfo();
  if (!session?.roles.includes("ADMIN")) {
    return NextResponse.json(
      { message: "해당 작업을 수행할 권한이 없습니다." },
      { status: 403 }
    );
  }

  return forwardBeResponse(`/admin/users/${params.userId}/roles/${params.roleName}`, {
    method: "POST",
  });
}

export async function DELETE(
  _request: NextRequest,
  { params }: RouteContext
): Promise<NextResponse> {
  const session = getSessionInfo();
  if (!session?.roles.includes("ADMIN")) {
    return NextResponse.json(
      { message: "해당 작업을 수행할 권한이 없습니다." },
      { status: 403 }
    );
  }

  return forwardBeResponse(`/admin/users/${params.userId}/roles/${params.roleName}`, {
    method: "DELETE",
  });
}

/**
 * BFF Route Handler — /api/admin/feature-flags/[key]/activate
 * POST : 재활성화 → BE POST /admin/feature-flags/{key}/activate forward
 */
import { NextRequest, NextResponse } from "next/server";
import { forwardBeResponse } from "@/app/api/portal/_lib/bff-helpers";

interface RouteContext {
  params: { key: string };
}

export async function POST(
  _request: NextRequest,
  { params }: RouteContext
): Promise<NextResponse> {
  return forwardBeResponse(`/admin/feature-flags/${params.key}/activate`, { method: "POST" });
}

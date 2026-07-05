/**
 * BFF Route Handler — /api/admin/feature-flags/[key]/archive
 * POST : 아카이브 → BE POST /admin/feature-flags/{key}/archive forward
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
  return forwardBeResponse(`/admin/feature-flags/${params.key}/archive`, { method: "POST" });
}

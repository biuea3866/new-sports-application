/**
 * BFF Route Handler — /api/admin/feature-flags/[key]
 * GET : 상세 조회 → BE GET /admin/feature-flags/{key} forward
 * PUT : 수정 → UpdateFeatureFlagInputSchema.parse 후 BE PUT /admin/feature-flags/{key} forward
 */
import { NextRequest, NextResponse } from "next/server";
import { ZodError } from "zod";
import { UpdateFeatureFlagInputSchema } from "@/lib/admin/feature-flags/schemas";
import { forwardBeResponse, zodValidationError } from "@/app/api/portal/_lib/bff-helpers";

interface RouteContext {
  params: { key: string };
}

export async function GET(
  _request: NextRequest,
  { params }: RouteContext
): Promise<NextResponse> {
  return forwardBeResponse(`/admin/feature-flags/${params.key}`, { method: "GET" });
}

export async function PUT(
  request: NextRequest,
  { params }: RouteContext
): Promise<NextResponse> {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ message: "요청 본문이 유효한 JSON이 아닙니다." }, { status: 400 });
  }

  try {
    UpdateFeatureFlagInputSchema.parse(body);
  } catch (error) {
    if (error instanceof ZodError) return zodValidationError(error);
    throw error;
  }

  return forwardBeResponse(`/admin/feature-flags/${params.key}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

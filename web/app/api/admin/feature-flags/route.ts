/**
 * BFF Route Handler — /api/admin/feature-flags
 * GET  : 목록 조회 (?status=&type=) → BE GET /admin/feature-flags forward
 * POST : 생성 → CreateFeatureFlagInputSchema.parse 후 BE POST /admin/feature-flags forward
 */
import { NextRequest, NextResponse } from "next/server";
import { ZodError } from "zod";
import { CreateFeatureFlagInputSchema } from "@/lib/admin/feature-flags/schemas";
import { forwardBeResponse, zodValidationError } from "@/app/api/portal/_lib/bff-helpers";

export async function GET(request: NextRequest): Promise<NextResponse> {
  const { searchParams } = request.nextUrl;
  const query = searchParams.toString();
  const path = query ? `/admin/feature-flags?${query}` : "/admin/feature-flags";

  return forwardBeResponse(path, { method: "GET" });
}

export async function POST(request: NextRequest): Promise<NextResponse> {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ message: "요청 본문이 유효한 JSON이 아닙니다." }, { status: 400 });
  }

  try {
    CreateFeatureFlagInputSchema.parse(body);
  } catch (error) {
    if (error instanceof ZodError) return zodValidationError(error);
    throw error;
  }

  return forwardBeResponse("/admin/feature-flags", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

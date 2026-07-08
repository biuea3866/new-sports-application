/**
 * BFF Route Handler — /api/portal/facilities/[id]/programs
 * GET  : 시설상품 목록 조회 → BE GET /facilities/{facilityId}/programs forward (공개, 인증 불요)
 * POST : 시설상품 등록 → BE POST /facilities/{facilityId}/programs forward
 */
import { NextRequest, NextResponse } from "next/server";
import { ZodError } from "zod";
import { CreateProgramInputSchema } from "@/lib/portal/schemas";
import { forwardBeResponse, zodValidationError } from "@/app/api/portal/_lib/bff-helpers";

interface RouteContext {
  params: { id: string };
}

export async function GET(
  _request: NextRequest,
  { params }: RouteContext
): Promise<NextResponse> {
  return forwardBeResponse(`/facilities/${params.id}/programs`, { method: "GET" });
}

export async function POST(
  request: NextRequest,
  { params }: RouteContext
): Promise<NextResponse> {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ message: "요청 본문이 유효한 JSON이 아닙니다." }, { status: 400 });
  }

  let input: ReturnType<typeof CreateProgramInputSchema.parse>;
  try {
    input = CreateProgramInputSchema.parse(body);
  } catch (error) {
    if (error instanceof ZodError) return zodValidationError(error);
    throw error;
  }

  return forwardBeResponse(`/facilities/${params.id}/programs`, {
    method: "POST",
    body: JSON.stringify(input),
  });
}

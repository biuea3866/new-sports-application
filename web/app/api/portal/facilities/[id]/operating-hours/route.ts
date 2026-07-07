/**
 * BFF Route Handler — /api/portal/facilities/[id]/operating-hours
 * PUT : 요일별 운영시간 등록/수정 → BE PUT /facilities/{facilityId}/operating-hours forward
 */
import { NextRequest, NextResponse } from "next/server";
import { ZodError } from "zod";
import { RegisterOperatingHoursInputSchema } from "@/lib/portal/schemas";
import { forwardBeResponse, zodValidationError } from "@/app/api/portal/_lib/bff-helpers";

interface RouteContext {
  params: { id: string };
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

  let input: ReturnType<typeof RegisterOperatingHoursInputSchema.parse>;
  try {
    input = RegisterOperatingHoursInputSchema.parse(body);
  } catch (error) {
    if (error instanceof ZodError) return zodValidationError(error);
    throw error;
  }

  return forwardBeResponse(`/facilities/${params.id}/operating-hours`, {
    method: "PUT",
    body: JSON.stringify(input),
  });
}

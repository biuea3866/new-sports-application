/**
 * BFF Route Handler — /api/portal/facilities/[id]/holidays
 * POST   : 휴무일 추가 → BE POST /facilities/{facilityId}/holidays forward
 * DELETE : 휴무일 삭제 → BE DELETE /facilities/{facilityId}/holidays?date=... forward
 *   (BE RemoveHolidayRequest는 @ModelAttribute — query param으로 전달한다)
 */
import { NextRequest, NextResponse } from "next/server";
import { ZodError } from "zod";
import { HolidayInputSchema } from "@/lib/portal/schemas";
import { forwardBeResponse, zodValidationError } from "@/app/api/portal/_lib/bff-helpers";

interface RouteContext {
  params: { id: string };
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

  let input: ReturnType<typeof HolidayInputSchema.parse>;
  try {
    input = HolidayInputSchema.parse(body);
  } catch (error) {
    if (error instanceof ZodError) return zodValidationError(error);
    throw error;
  }

  return forwardBeResponse(`/facilities/${params.id}/holidays`, {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export async function DELETE(
  request: NextRequest,
  { params }: RouteContext
): Promise<NextResponse> {
  const date = request.nextUrl.searchParams.get("date");
  const parsed = HolidayInputSchema.safeParse({ date });
  if (!parsed.success) {
    return zodValidationError(parsed.error);
  }

  return forwardBeResponse(
    `/facilities/${params.id}/holidays?date=${encodeURIComponent(parsed.data.date)}`,
    { method: "DELETE" }
  );
}

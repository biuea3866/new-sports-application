import { NextRequest, NextResponse } from "next/server";
import { ZodError } from "zod";
import { UpdateFacilityInputSchema } from "@/lib/portal/schemas";
import { forwardBeResponse, zodValidationError } from "../../_lib/bff-helpers";

interface RouteContext {
  params: { id: string };
}

export async function GET(
  _request: NextRequest,
  { params }: RouteContext
): Promise<NextResponse> {
  return forwardBeResponse(`/api/facility-owner/facilities/${params.id}`, { method: "GET" });
}

export async function PATCH(
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
    UpdateFacilityInputSchema.parse(body);
  } catch (error) {
    if (error instanceof ZodError) return zodValidationError(error);
    throw error;
  }

  return forwardBeResponse(`/api/facility-owner/facilities/${params.id}`, {
    method: "PATCH",
    body: JSON.stringify(body),
  });
}

export async function DELETE(
  _request: NextRequest,
  { params }: RouteContext
): Promise<NextResponse> {
  return forwardBeResponse(`/api/facility-owner/facilities/${params.id}`, { method: "DELETE" });
}

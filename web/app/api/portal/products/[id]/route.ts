import { NextRequest, NextResponse } from "next/server";
import { ZodError } from "zod";
import { UpdateProductInputSchema } from "@/lib/portal/schemas";
import { forwardBeResponse, zodValidationError } from "../../_lib/bff-helpers";

interface RouteContext {
  params: { id: string };
}

export async function GET(
  _request: NextRequest,
  { params }: RouteContext
): Promise<NextResponse> {
  return forwardBeResponse(`/api/goods-seller/products/${params.id}`, { method: "GET" });
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
    UpdateProductInputSchema.parse(body);
  } catch (error) {
    if (error instanceof ZodError) return zodValidationError(error);
    throw error;
  }

  return forwardBeResponse(`/api/goods-seller/products/${params.id}`, {
    method: "PATCH",
    body: JSON.stringify(body),
  });
}

import { NextRequest, NextResponse } from "next/server";
import { ZodError } from "zod";
import { RestoreStockInputSchema } from "@/lib/portal/schemas";
import { forwardBeResponse, zodValidationError } from "../../../../_lib/bff-helpers";

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

  try {
    RestoreStockInputSchema.parse(body);
  } catch (error) {
    if (error instanceof ZodError) return zodValidationError(error);
    throw error;
  }

  return forwardBeResponse(`/api/b2b/products/${params.id}/stock/restore`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

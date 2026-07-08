import { NextRequest, NextResponse } from "next/server";
import { ZodError } from "zod";
import { IssueMcpTokenInputSchema } from "@/lib/admin/mcp/schemas";
import { forwardBeResponse, zodValidationError } from "@/app/api/portal/_lib/bff-helpers";

export async function GET(): Promise<NextResponse> {
  return forwardBeResponse("/api/admin/mcp/tokens", { method: "GET" });
}

export async function POST(request: NextRequest): Promise<NextResponse> {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ message: "요청 본문이 유효한 JSON이 아닙니다." }, { status: 400 });
  }

  try {
    IssueMcpTokenInputSchema.parse(body);
  } catch (error) {
    if (error instanceof ZodError) return zodValidationError(error);
    throw error;
  }

  return forwardBeResponse("/api/admin/mcp/tokens", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

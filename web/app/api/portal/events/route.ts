import { NextRequest, NextResponse } from "next/server";
import { ZodError } from "zod";
import { CreateEventInputSchema } from "@/lib/portal/schemas";
import { forwardBeResponse, zodValidationError } from "../_lib/bff-helpers";

export async function GET(request: NextRequest): Promise<NextResponse> {
  const { searchParams } = request.nextUrl;
  const query = searchParams.toString();
  const path = query ? `/api/b2b/events?${query}` : "/api/b2b/events";
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
    CreateEventInputSchema.parse(body);
  } catch (error) {
    if (error instanceof ZodError) return zodValidationError(error);
    throw error;
  }

  return forwardBeResponse("/api/b2b/events", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

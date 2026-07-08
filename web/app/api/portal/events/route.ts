import { NextRequest, NextResponse } from "next/server";
import { ZodError } from "zod";
import { CreateEventInputSchema } from "@/lib/portal/schemas";
import { forwardBeResponse, zodValidationError } from "../_lib/bff-helpers";

export async function GET(request: NextRequest): Promise<NextResponse> {
  const { searchParams } = request.nextUrl;
  const query = searchParams.toString();
  const path = query ? `/api/event-host/events?${query}` : "/api/event-host/events";
  return forwardBeResponse(path, { method: "GET" });
}

export async function POST(request: NextRequest): Promise<NextResponse> {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ message: "요청 본문이 유효한 JSON이 아닙니다." }, { status: 400 });
  }

  let input: ReturnType<typeof CreateEventInputSchema.parse>;
  try {
    input = CreateEventInputSchema.parse(body);
  } catch (error) {
    if (error instanceof ZodError) return zodValidationError(error);
    throw error;
  }

  // FE는 좌석을 라벨 문자열 배열로 보낸다. BE CreateMyEventRequest는
  // 좌석마다 {sectionName, seatLabel, price}를 요구하므로 변환한다.
  const bePayload = {
    title: input.title,
    venue: input.venue,
    startsAt: input.startsAt,
    seats: input.seats.map((label) => ({
      sectionName: input.section,
      seatLabel: label,
      price: input.price,
    })),
  };

  return forwardBeResponse("/api/event-host/events", {
    method: "POST",
    body: JSON.stringify(bePayload),
  });
}

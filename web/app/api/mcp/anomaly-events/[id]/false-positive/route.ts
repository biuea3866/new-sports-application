import { NextRequest, NextResponse } from "next/server";
import { forwardBeResponse } from "@/app/api/portal/_lib/bff-helpers";

export async function POST(
  request: NextRequest,
  { params }: { params: { id: string } }
): Promise<NextResponse> {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json(
      { message: "요청 본문이 유효한 JSON이 아닙니다." },
      { status: 400 }
    );
  }

  return forwardBeResponse(
    `/api/mcp/anomaly-events/${params.id}/false-positive`,
    {
      method: "POST",
      body: JSON.stringify(body),
    }
  );
}

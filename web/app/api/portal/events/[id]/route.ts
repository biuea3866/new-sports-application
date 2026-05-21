import { NextRequest, NextResponse } from "next/server";
import { forwardBeResponse } from "../../_lib/bff-helpers";

interface RouteContext {
  params: { id: string };
}

export async function GET(
  _request: NextRequest,
  { params }: RouteContext
): Promise<NextResponse> {
  return forwardBeResponse(`/api/b2b/events/${params.id}`, { method: "GET" });
}

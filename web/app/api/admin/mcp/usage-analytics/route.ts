import { NextRequest, NextResponse } from "next/server";
import { forwardBeResponse } from "@/app/api/portal/_lib/bff-helpers";

export async function GET(request: NextRequest): Promise<NextResponse> {
  const { searchParams } = request.nextUrl;
  const query = searchParams.toString();
  const path = query
    ? `/api/admin/mcp/usage-analytics?${query}`
    : "/api/admin/mcp/usage-analytics";
  return forwardBeResponse(path, { method: "GET" });
}

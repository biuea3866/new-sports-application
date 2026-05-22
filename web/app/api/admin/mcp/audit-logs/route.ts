import { NextRequest, NextResponse } from "next/server";
import { forwardBeResponse } from "@/app/api/portal/_lib/bff-helpers";

export async function GET(request: NextRequest): Promise<NextResponse> {
  const { searchParams } = request.nextUrl;
  const query = searchParams.toString();
  const path = query
    ? `/api/admin/mcp/audit-logs?${query}`
    : "/api/admin/mcp/audit-logs";
  return forwardBeResponse(path, { method: "GET" });
}

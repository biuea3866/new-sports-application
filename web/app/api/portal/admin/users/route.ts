import { NextRequest, NextResponse } from "next/server";
import { getSessionInfo } from "@/lib/server/auth";
import { forwardBeResponse } from "../../_lib/bff-helpers";

export async function GET(request: NextRequest): Promise<NextResponse> {
  const session = getSessionInfo();
  if (!session?.roles.includes("ADMIN")) {
    return NextResponse.json(
      { message: "해당 작업을 수행할 권한이 없습니다." },
      { status: 403 }
    );
  }

  const { searchParams } = request.nextUrl;
  const query = searchParams.toString();
  const path = query ? `/admin/users?${query}` : "/admin/users";

  return forwardBeResponse(path, { method: "GET" });
}

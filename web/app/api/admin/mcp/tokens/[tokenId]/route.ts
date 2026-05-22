import { NextResponse } from "next/server";
import { forwardBeResponse } from "@/app/api/portal/_lib/bff-helpers";

interface RouteContext {
  params: { tokenId: string };
}

export async function DELETE(_request: Request, context: RouteContext): Promise<NextResponse> {
  const { tokenId } = context.params;
  if (!/^\d+$/.test(tokenId)) {
    return NextResponse.json({ message: "유효하지 않은 tokenId입니다." }, { status: 400 });
  }
  return forwardBeResponse(`/api/admin/mcp/tokens/${tokenId}`, { method: "DELETE" });
}

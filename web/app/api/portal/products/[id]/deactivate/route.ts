import { NextRequest, NextResponse } from "next/server";
import { forwardBeResponse } from "../../../_lib/bff-helpers";

interface RouteContext {
  params: { id: string };
}

export async function POST(
  _request: NextRequest,
  { params }: RouteContext
): Promise<NextResponse> {
  return forwardBeResponse(`/api/goods-seller/products/${params.id}/deactivate`, { method: "POST" });
}

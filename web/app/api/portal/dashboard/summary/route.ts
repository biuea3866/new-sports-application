import { NextResponse } from "next/server";
import { forwardBeResponse } from "../../_lib/bff-helpers";

export async function GET(): Promise<NextResponse> {
  return forwardBeResponse("/api/b2b/dashboard/summary", { method: "GET" });
}

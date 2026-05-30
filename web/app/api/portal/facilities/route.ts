import { NextRequest, NextResponse } from "next/server";
import { ZodError } from "zod";
import { CreateFacilityInputSchema } from "@/lib/portal/schemas";
import type { CreateFacilityInput } from "@/lib/portal/types";
import { forwardBeResponse, zodValidationError } from "../_lib/bff-helpers";

export async function GET(request: NextRequest): Promise<NextResponse> {
  const { searchParams } = request.nextUrl;
  const query = searchParams.toString();
  const path = query ? `/api/facility-owner/facilities?${query}` : "/api/facility-owner/facilities";
  return forwardBeResponse(path, { method: "GET" });
}

/**
 * FE 폼 입력(location "lat,lng" 문자열 + meta JSON 문자열)을
 * BE RegisterFacilityRequest 형태(lat/lng number + meta object)로 변환한다.
 */
function toBeFacilityPayload(input: CreateFacilityInput): Record<string, unknown> | null {
  const [latRaw, lngRaw] = input.location.split(",").map((s) => s.trim());
  const lat = Number(latRaw);
  const lng = Number(lngRaw);
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;

  let meta: Record<string, string> = {};
  if (input.meta && input.meta.trim().length > 0) {
    try {
      const parsed: unknown = JSON.parse(input.meta);
      if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
        meta = parsed as Record<string, string>;
      }
    } catch {
      return null;
    }
  }

  return {
    code: input.code,
    name: input.name,
    gu: input.gu,
    type: input.type,
    address: input.address,
    lat,
    lng,
    parking: input.parking,
    tel: input.tel,
    homePage: input.homePage ?? "",
    eduYn: input.eduYn,
    meta,
  };
}

export async function POST(request: NextRequest): Promise<NextResponse> {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ message: "요청 본문이 유효한 JSON이 아닙니다." }, { status: 400 });
  }

  let input: CreateFacilityInput;
  try {
    input = CreateFacilityInputSchema.parse(body);
  } catch (error) {
    if (error instanceof ZodError) return zodValidationError(error);
    throw error;
  }

  const bePayload = toBeFacilityPayload(input);
  if (bePayload === null) {
    return NextResponse.json(
      { message: "위치는 '위도,경도' 형식, 메타는 JSON 객체 형식이어야 합니다." },
      { status: 400 }
    );
  }

  return forwardBeResponse("/api/facility-owner/facilities", {
    method: "POST",
    body: JSON.stringify(bePayload),
  });
}

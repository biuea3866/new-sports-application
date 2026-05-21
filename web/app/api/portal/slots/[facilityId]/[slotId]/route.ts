/**
 * BFF Route Handler — /api/portal/slots/[facilityId]/[slotId]
 * PATCH  : 슬롯 수정 → BE PATCH /facilities/{facilityId}/slots/{slotId} forward
 * DELETE : 슬롯 삭제 → BE DELETE /facilities/{facilityId}/slots/{slotId} forward
 */
import { NextRequest, NextResponse } from "next/server";
import { z } from "zod";

const UpdateSlotInputSchema = z
  .object({
    timeRange: z.string().min(1).optional(),
    capacity: z.number().int().positive().optional(),
  })
  .refine((data) => data.timeRange !== undefined || data.capacity !== undefined, {
    message: "수정할 필드가 최소 1개 이상 있어야 합니다.",
  });

async function getBeClient() {
  if (!process.env["BACKEND_URL"]) return null;
  const { beClient } = await import("@/lib/server/be-client");
  return beClient;
}

async function forwardBe(path: string, init: { method: string; body?: string }): Promise<NextResponse> {
  const client = await getBeClient();
  if (!client) {
    return NextResponse.json(
      { message: "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요." },
      { status: 503 }
    );
  }

  let beRes: Response;
  try {
    beRes = await client(path, { method: init.method, body: init.body });
  } catch {
    return NextResponse.json(
      { message: "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요." },
      { status: 503 }
    );
  }

  const status = beRes.status;
  if (status === 204) return new NextResponse(null, { status: 204 });
  if (status >= 500) {
    return NextResponse.json(
      { message: "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요." },
      { status: 500 }
    );
  }
  if (status === 401) {
    const wwwAuth = beRes.headers.get("WWW-Authenticate");
    return NextResponse.json(
      { message: "로그인이 필요합니다." },
      { status: 401, headers: wwwAuth ? { "WWW-Authenticate": wwwAuth } : {} }
    );
  }
  if (status === 403) {
    return NextResponse.json({ message: "해당 작업을 수행할 권한이 없습니다." }, { status: 403 });
  }
  if (status >= 400) {
    let detail: unknown;
    try {
      detail = (await beRes.json() as { detail?: unknown }).detail;
    } catch {
      // 응답 body 파싱 실패는 무시
    }
    return NextResponse.json(
      { message: "잘못된 요청입니다. 입력 값을 확인해 주세요.", detail },
      { status }
    );
  }

  const body = await beRes.json() as unknown;
  return NextResponse.json(body, { status });
}

export async function PATCH(
  request: NextRequest,
  { params }: { params: { facilityId: string; slotId: string } }
): Promise<NextResponse> {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ message: "요청 본문이 유효한 JSON이 아닙니다." }, { status: 400 });
  }

  const parsed = UpdateSlotInputSchema.safeParse(body);
  if (!parsed.success) {
    return NextResponse.json(
      {
        message: "잘못된 요청입니다. 입력 값을 확인해 주세요.",
        errors: parsed.error.flatten().fieldErrors,
      },
      { status: 400 }
    );
  }

  return forwardBe(`/facilities/${params.facilityId}/slots/${params.slotId}`, {
    method: "PATCH",
    body: JSON.stringify(parsed.data),
  });
}

export async function DELETE(
  _request: NextRequest,
  { params }: { params: { facilityId: string; slotId: string } }
): Promise<NextResponse> {
  return forwardBe(`/facilities/${params.facilityId}/slots/${params.slotId}`, {
    method: "DELETE",
  });
}

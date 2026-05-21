/**
 * BFF Route Handler — /api/portal/bookings/[id]
 * GET : 예약 단건 조회 → BE GET /bookings/{id} forward
 */
import { NextRequest, NextResponse } from "next/server";

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
    beRes = await client(path, init);
  } catch {
    return NextResponse.json(
      { message: "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요." },
      { status: 503 }
    );
  }

  const status = beRes.status;
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
  if (status === 404) {
    return NextResponse.json({ message: "예약을 찾을 수 없습니다." }, { status: 404 });
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

export async function GET(
  _request: NextRequest,
  { params }: { params: { id: string } }
): Promise<NextResponse> {
  return forwardBe(`/bookings/${params.id}`, { method: "GET" });
}

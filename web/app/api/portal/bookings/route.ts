/**
 * BFF Route Handler — /api/portal/bookings
 * GET : 파트너 소유 시설의 예약 목록 조회 → BE GET /api/facility-owner/bookings forward
 *
 * 쿼리 파라미터: status, page, size
 *
 * 예약자 스코프(`/bookings/me`)가 아니다 — 포털 "예약 관리"는 **내 시설에 들어온 남의 예약**을
 * 봐야 한다. 예약자 스코프를 호출하면 파트너 본인 예약만 잡혀 소유 시설에 예약이 실재해도
 * 0건으로 보인다. 조회 대상 소유자는 BE가 인증 주체로 결정하므로 여기서 넘기지 않는다.
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
    beRes = await client(path, { method: init.method, body: init.body });
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

export async function GET(request: NextRequest): Promise<NextResponse> {
  const { searchParams } = request.nextUrl;
  const qs = searchParams.toString();
  const bePath = qs
    ? `/api/facility-owner/bookings?${qs}`
    : "/api/facility-owner/bookings";
  return forwardBe(bePath, { method: "GET" });
}

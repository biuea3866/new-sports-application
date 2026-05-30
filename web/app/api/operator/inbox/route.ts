/**
 * BFF Route Handler — /api/operator/inbox
 * GET : 운영자 알림 목록 조회 → BE GET /operator/inbox forward
 * BE는 X-User-Id 헤더를 요구한다 — JWT의 sub에서 추출해 부착한다.
 *
 * 쿼리 파라미터: type, status, page, size
 */
import { NextRequest, NextResponse } from "next/server";
import { getSessionInfo } from "@/lib/server/auth";

async function getBeClient() {
  if (!process.env["BACKEND_URL"]) return null;
  const { beClient } = await import("@/lib/server/be-client");
  return beClient;
}

export async function GET(request: NextRequest): Promise<NextResponse> {
  const session = getSessionInfo();
  if (!session) {
    return NextResponse.json({ message: "로그인이 필요합니다." }, { status: 401 });
  }

  const client = await getBeClient();
  if (!client) {
    return NextResponse.json(
      { message: "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요." },
      { status: 503 }
    );
  }

  const { searchParams } = request.nextUrl;
  const query = searchParams.toString();
  const bePath = query ? `/operator/inbox?${query}` : "/operator/inbox";

  let beRes: Response;
  try {
    beRes = await client(bePath, {
      method: "GET",
      headers: { "X-User-Id": String(session.userId) },
    });
  } catch {
    return NextResponse.json(
      { message: "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요." },
      { status: 503 }
    );
  }

  if (beRes.status >= 500) {
    return NextResponse.json(
      { message: "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요." },
      { status: 500 }
    );
  }
  if (beRes.status === 401) {
    return NextResponse.json({ message: "로그인이 필요합니다." }, { status: 401 });
  }
  if (beRes.status === 403) {
    return NextResponse.json({ message: "해당 작업을 수행할 권한이 없습니다." }, { status: 403 });
  }
  if (beRes.status >= 400) {
    let detail: unknown;
    try {
      detail = (await beRes.json() as { detail?: unknown }).detail;
    } catch {
      // JSON 파싱 실패 무시
    }
    return NextResponse.json(
      { message: "잘못된 요청입니다. 입력 값을 확인해 주세요.", detail },
      { status: beRes.status }
    );
  }

  const body: unknown = await beRes.json();
  return NextResponse.json(body, { status: beRes.status });
}

/**
 * BFF Route Handler — /api/operator/inbox/[id]/read
 * PATCH : 알림 읽음 처리 → BE PATCH /operator/inbox/{id}/read forward
 * BE는 X-User-Id 헤더를 요구한다 — JWT의 sub에서 추출해 부착한다.
 */
import { NextRequest, NextResponse } from "next/server";
import { getSessionInfo } from "@/lib/server/auth";

async function getBeClient() {
  if (!process.env["BACKEND_URL"]) return null;
  const { beClient } = await import("@/lib/server/be-client");
  return beClient;
}

export async function PATCH(
  _request: NextRequest,
  { params }: { params: { id: string } }
): Promise<NextResponse> {
  const session = getSessionInfo();
  if (!session) {
    return NextResponse.json({ message: "로그인이 필요합니다." }, { status: 401 });
  }

  const notificationId = Number(params.id);
  if (!Number.isFinite(notificationId) || notificationId <= 0) {
    return NextResponse.json({ message: "유효하지 않은 알림 ID입니다." }, { status: 400 });
  }

  const client = await getBeClient();
  if (!client) {
    return NextResponse.json(
      { message: "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요." },
      { status: 503 }
    );
  }

  let beRes: Response;
  try {
    beRes = await client(`/operator/inbox/${notificationId}/read`, {
      method: "PATCH",
      headers: { "X-User-Id": String(session.userId) },
    });
  } catch {
    return NextResponse.json(
      { message: "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요." },
      { status: 503 }
    );
  }

  if (beRes.status === 204) {
    return new NextResponse(null, { status: 204 });
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
  if (beRes.status === 404) {
    return NextResponse.json({ message: "요청한 리소스를 찾을 수 없습니다." }, { status: 404 });
  }
  if (beRes.status >= 400) {
    return NextResponse.json(
      { message: "잘못된 요청입니다. 입력 값을 확인해 주세요." },
      { status: beRes.status }
    );
  }

  const contentType = beRes.headers.get("content-type") ?? "";
  if (contentType.includes("application/json")) {
    const body: unknown = await beRes.json();
    return NextResponse.json(body, { status: beRes.status });
  }

  return new NextResponse(null, { status: beRes.status });
}

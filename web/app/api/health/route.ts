import { NextResponse } from "next/server";

interface BeHealthResponse {
  status: string;
}

interface HealthResponse {
  web: "ok" | "error";
  be: "ok" | "error" | "unavailable";
  timestamp: string;
}

/**
 * GET /api/health
 * Web 자체 상태 + BE /actuator/health 상태를 합산해 반환한다.
 * BE 호출은 반드시 lib/server/be-client.ts 의 beClient 를 경유한다 (BFF 단일 진입점).
 * BE 가 응답 불능이면 be: "unavailable" 로 처리하고 web 상태는 정상 반환한다.
 */
export async function GET(): Promise<NextResponse<HealthResponse>> {
  const backendUrl = process.env["BACKEND_URL"];
  const timestamp = new Date().toISOString();

  if (!backendUrl) {
    return NextResponse.json(
      { web: "ok", be: "unavailable", timestamp },
      { status: 200 }
    );
  }

  // BACKEND_URL 이 존재할 때만 be-client 모듈 평가 — 모듈 로드 throw 회피
  const { beClient } = await import("@/lib/server/be-client");

  try {
    const beResponse = await beClient("/actuator/health", {
      method: "GET",
      cache: "no-store",
      timeoutMs: 5000,
    });

    if (!beResponse.ok) {
      return NextResponse.json({ web: "ok", be: "error", timestamp }, { status: 200 });
    }

    const beHealth = (await beResponse.json()) as BeHealthResponse;
    const beStatus = beHealth.status === "UP" ? "ok" : "error";

    return NextResponse.json({ web: "ok", be: beStatus, timestamp }, { status: 200 });
  } catch {
    return NextResponse.json({ web: "ok", be: "unavailable", timestamp }, { status: 200 });
  }
}

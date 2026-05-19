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
 * BE가 응답 불능이면 be: "unavailable"로 처리하고 web 상태는 정상 반환한다.
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

  try {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 5000);

    const beResponse = await fetch(`${backendUrl}/actuator/health`, {
      method: "GET",
      headers: { "Content-Type": "application/json" },
      signal: controller.signal,
      cache: "no-store",
    });
    clearTimeout(timeoutId);

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

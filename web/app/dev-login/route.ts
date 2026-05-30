/**
 * dev-login — 로컬 데모 전용 자동 로그인 라우트
 *
 * 웹 B2B 포털은 로그인 폼이 없고 access_token 쿠키를 요구한다.
 * 로컬에서 포털을 바로 둘러볼 수 있도록, demo 운영자 계정으로 BE 로그인 후
 * 쿠키를 심고 /portal로 리다이렉트한다.
 *
 * 주의: 데모/로컬 전용. 운영 빌드에 포함되면 안 된다 (demo 브랜치에만 존재).
 */
import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const DEMO_EMAIL = "demo-user@test.local";
const DEMO_PASSWORD = "Passw0rd!";

export async function GET(request: Request) {
  // 운영 빌드에서는 고정 자격증명 자동 로그인 백도어를 차단한다.
  // 데모/로컬(NODE_ENV !== production)에서만 동작.
  if (process.env.NODE_ENV === "production") {
    return new NextResponse(null, { status: 404 });
  }

  const backendUrl = process.env["BACKEND_URL"] ?? "http://localhost:8080";

  const loginRes = await fetch(`${backendUrl}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email: DEMO_EMAIL, password: DEMO_PASSWORD }),
    cache: "no-store",
  });

  if (!loginRes.ok) {
    const body = await loginRes.text();
    return NextResponse.json(
      { error: "demo login failed", status: loginRes.status, body },
      { status: 502 }
    );
  }

  const data = (await loginRes.json()) as { accessToken: string; refreshToken?: string };

  const response = NextResponse.redirect(new URL("/portal", request.url));
  response.cookies.set("access_token", data.accessToken, {
    path: "/",
    httpOnly: true,
    sameSite: "lax",
  });
  if (data.refreshToken) {
    response.cookies.set("refresh_token", data.refreshToken, {
      path: "/",
      httpOnly: true,
      sameSite: "lax",
    });
  }
  return response;
}

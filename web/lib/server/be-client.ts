/**
 * BFF 전용 BE API 클라이언트.
 * 반드시 서버 사이드(Server Component, Route Handler)에서만 사용한다.
 * Client Component에서 직접 import 금지 — BFF 패턴 강제.
 */
import { cookies } from "next/headers";

const BACKEND_URL = process.env["BACKEND_URL"];

if (!BACKEND_URL) {
  throw new Error(
    "[be-client] BACKEND_URL 환경 변수가 설정되지 않았습니다. .env.local 또는 환경 변수를 확인하세요."
  );
}

/** 서버 사이드에서 JWT 액세스 토큰을 httpOnly 쿠키에서 추출한다. */
function extractAccessToken(): string | undefined {
  const cookieStore = cookies();
  return cookieStore.get("access_token")?.value;
}

interface BeRequestInit extends Omit<RequestInit, "headers"> {
  headers?: Record<string, string>;
}

/**
 * BE API를 호출하는 단일 fetch 인스턴스.
 * JWT 쿠키가 존재하면 Authorization: Bearer <token> 헤더를 자동 부착한다.
 */
export async function beClient(path: string, init: BeRequestInit = {}): Promise<Response> {
  const baseUrl = BACKEND_URL as string;
  const url = `${baseUrl}${path}`;

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...init.headers,
  };

  const token = extractAccessToken();
  if (token !== undefined) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  return fetch(url, {
    ...init,
    headers,
  });
}

/** JSON 응답을 파싱해 반환하는 편의 래퍼. */
export async function beGet<T>(path: string, init?: BeRequestInit): Promise<T> {
  const response = await beClient(path, { ...init, method: "GET" });
  if (!response.ok) {
    throw new Error(`[be-client] GET ${path} failed: ${response.status} ${response.statusText}`);
  }
  return response.json() as Promise<T>;
}

/** JSON body를 POST하고 응답을 파싱해 반환하는 편의 래퍼. */
export async function bePost<T>(path: string, body: unknown, init?: BeRequestInit): Promise<T> {
  const response = await beClient(path, {
    ...init,
    method: "POST",
    body: JSON.stringify(body),
  });
  if (!response.ok) {
    throw new Error(`[be-client] POST ${path} failed: ${response.status} ${response.statusText}`);
  }
  return response.json() as Promise<T>;
}

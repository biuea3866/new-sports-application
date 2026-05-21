/**
 * BFF 전용 BE API 클라이언트.
 * 반드시 서버 사이드(Server Component, Route Handler)에서만 사용한다.
 * `server-only` import 로 Client Component 에서 import 시 빌드 실패시켜 BFF 패턴을 강제한다.
 */
import "server-only";
import { cookies } from "next/headers";

/** 서버 사이드에서 JWT 액세스 토큰을 httpOnly 쿠키에서 추출한다. */
function extractAccessToken(): string | undefined {
  const cookieStore = cookies();
  return cookieStore.get("access_token")?.value;
}

interface BeRequestInit extends Omit<RequestInit, "headers"> {
  headers?: Record<string, string>;
  timeoutMs?: number;
}

const DEFAULT_TIMEOUT_MS = 5000;

/**
 * BE API 를 호출하는 단일 fetch 인스턴스.
 * JWT 쿠키가 존재하면 Authorization: Bearer <token> 헤더를 자동 부착한다.
 * timeoutMs(기본 5초) 초과 시 AbortError 를 throw 한다.
 * BACKEND_URL 이 설정되지 않으면 호출 시점에 Error 를 throw 한다 (모듈 로드 시 throw 금지).
 */
export async function beClient(path: string, init: BeRequestInit = {}): Promise<Response> {
  const backendUrl = process.env["BACKEND_URL"];
  if (!backendUrl) {
    throw new Error(
      "[be-client] BACKEND_URL 환경 변수가 설정되지 않았습니다. .env.local 또는 환경 변수를 확인하세요."
    );
  }
  const url = `${backendUrl}${path}`;

  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...init.headers,
  };

  const token = extractAccessToken();
  if (token !== undefined) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const { timeoutMs, ...rest } = init;
  const effectiveTimeout = timeoutMs ?? DEFAULT_TIMEOUT_MS;

  // 호출자가 signal 을 명시하지 않았으면 자체 timeout AbortController 부착
  let timeoutId: ReturnType<typeof setTimeout> | undefined;
  let signal = rest.signal;
  if (signal === undefined) {
    const controller = new AbortController();
    signal = controller.signal;
    timeoutId = setTimeout(() => controller.abort(), effectiveTimeout);
  }

  try {
    return await fetch(url, {
      ...rest,
      headers,
      signal,
    });
  } finally {
    if (timeoutId !== undefined) {
      clearTimeout(timeoutId);
    }
  }
}

/** JSON 응답을 파싱해 반환하는 편의 래퍼. */
export async function beGet<T>(path: string, init?: BeRequestInit): Promise<T> {
  const response = await beClient(path, { ...init, method: "GET" });
  if (!response.ok) {
    throw new Error(`[be-client] GET ${path} failed: ${response.status} ${response.statusText}`);
  }
  return response.json() as Promise<T>;
}

/** JSON body 를 POST 하고 응답을 파싱해 반환하는 편의 래퍼. */
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

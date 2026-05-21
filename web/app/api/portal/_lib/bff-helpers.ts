/**
 * BFF Route Handler 공통 헬퍼.
 * BE 응답을 그대로 NextResponse로 forward하고 에러를 사용자 친화 메시지로 매핑한다.
 *
 * beClient는 모듈 로드 시 BACKEND_URL 환경 변수가 없으면 throw한다.
 * 빌드 시 환경 변수가 없을 수 있으므로 dynamic import로 lazy 로드한다.
 */
import { NextResponse } from "next/server";
import { ZodError } from "zod";

import type { beClient as BeClientType } from "@/lib/server/be-client";
type BeClientFn = typeof BeClientType;

const STATUS_MESSAGES: Record<number, string> = {
  400: "잘못된 요청입니다. 입력 값을 확인해 주세요.",
  401: "로그인이 필요합니다.",
  403: "해당 작업을 수행할 권한이 없습니다.",
  404: "요청한 리소스를 찾을 수 없습니다.",
  409: "이미 존재하거나 충돌이 발생했습니다.",
};

function userMessageForStatus(status: number): string {
  return (
    STATUS_MESSAGES[status] ??
    "오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
  );
}

async function getBeClient(): Promise<BeClientFn | null> {
  const backendUrl = process.env["BACKEND_URL"];
  if (!backendUrl) return null;
  const beClientModule = await import("@/lib/server/be-client");
  return beClientModule.beClient;
}

/**
 * BE fetch 후 응답을 그대로 NextResponse로 forward한다.
 * - BACKEND_URL 미설정: 503 반환
 * - BE 4xx: 사용자 친화 메시지 + 원본 status 반환
 * - BE 401: WWW-Authenticate 헤더 포함
 * - BE 5xx: 500 + 사용자 친화 메시지 반환
 * - 성공: BE body를 그대로 반환
 */
export async function forwardBeResponse(
  bePath: string,
  init?: Record<string, unknown>
): Promise<NextResponse> {
  const beClient = await getBeClient();
  if (!beClient) {
    return NextResponse.json(
      { message: "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요." },
      { status: 503 }
    );
  }

  let beResponse: Response;
  try {
    beResponse = await beClient(bePath, init);
  } catch {
    return NextResponse.json(
      { message: "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요." },
      { status: 503 }
    );
  }

  const status = beResponse.status;

  // 204 No Content
  if (status === 204) {
    return new NextResponse(null, { status: 204 });
  }

  // 5xx — 사용자 친화 메시지로 대체
  if (status >= 500) {
    return NextResponse.json(
      { message: "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요." },
      { status: 500 }
    );
  }

  // 401 — WWW-Authenticate 헤더 포함 forward
  if (status === 401) {
    const wwwAuth = beResponse.headers.get("WWW-Authenticate");
    const headers: HeadersInit = {};
    if (wwwAuth) {
      headers["WWW-Authenticate"] = wwwAuth;
    }
    return NextResponse.json(
      { message: userMessageForStatus(401) },
      { status: 401, headers }
    );
  }

  // 4xx — 사용자 친화 메시지 + ProblemDetail detail 추가
  if (status >= 400) {
    let detail: string | undefined;
    try {
      const body = (await beResponse.json()) as { detail?: string };
      detail = body.detail;
    } catch {
      // JSON 파싱 실패는 무시
    }
    return NextResponse.json(
      { message: userMessageForStatus(status), detail },
      { status }
    );
  }

  // 2xx — BE body 그대로 반환
  const contentType = beResponse.headers.get("content-type") ?? "";
  if (contentType.includes("application/json")) {
    const body: unknown = await beResponse.json();
    return NextResponse.json(body, { status });
  }

  return new NextResponse(null, { status });
}

/** zod 입력 검증 실패를 400 응답으로 변환한다. */
export function zodValidationError(error: ZodError): NextResponse {
  return NextResponse.json(
    {
      message: "잘못된 요청입니다. 입력 값을 확인해 주세요.",
      errors: error.flatten().fieldErrors,
    },
    { status: 400 }
  );
}

/**
 * 서버 사이드 전용 인증 유틸리티.
 * access_token 쿠키에서 JWT 페이로드를 디코딩해 사용자 정보를 반환한다.
 * 검증(서명 확인)은 수행하지 않는다 — 인증 검증은 BE가 담당한다.
 */
import "server-only";
import { cookies } from "next/headers";

export type B2BRole = "FACILITY_OWNER" | "EVENT_HOST" | "GOODS_SELLER" | "ADMIN";

const B2B_ROLES: readonly B2BRole[] = ["FACILITY_OWNER", "EVENT_HOST", "GOODS_SELLER", "ADMIN"] as const;

export interface SessionInfo {
  userId: number;
  email: string;
  roles: string[];
}

/** Base64URL → Base64 변환 후 디코딩 */
function decodeBase64Url(input: string): string {
  const base64 = input.replace(/-/g, "+").replace(/_/g, "/");
  const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), "=");
  return Buffer.from(padded, "base64").toString("utf-8");
}

/**
 * access_token 쿠키를 읽어 JWT 페이로드를 반환한다.
 * 쿠키가 없거나 파싱에 실패하면 null을 반환한다.
 */
export function getSessionInfo(): SessionInfo | null {
  const token = cookies().get("access_token")?.value;
  if (!token) return null;

  const parts = token.split(".");
  if (parts.length !== 3) return null;

  try {
    const payloadStr = decodeBase64Url(parts[1] ?? "");
    const payload = JSON.parse(payloadStr) as {
      sub?: unknown;
      email?: unknown;
      roles?: unknown;
    };

    const userId = typeof payload.sub === "number" ? payload.sub : Number(payload.sub);
    const email = typeof payload.email === "string" ? payload.email : "";
    const roles = Array.isArray(payload.roles)
      ? (payload.roles as unknown[]).filter((r): r is string => typeof r === "string")
      : [];

    if (!Number.isFinite(userId) || !email) return null;

    return { userId, email, roles };
  } catch {
    return null;
  }
}

/**
 * 세션에서 B2B Role 목록을 반환한다.
 * 미인증이면 빈 배열을 반환한다.
 */
export function getB2BRoles(): B2BRole[] {
  const session = getSessionInfo();
  if (!session) return [];
  return B2B_ROLES.filter((role) => session.roles.includes(role));
}

/** 사용자가 주어진 B2B Role을 하나라도 보유하는지 확인한다. */
export function hasAnyB2BRole(): boolean {
  return getB2BRoles().length > 0;
}

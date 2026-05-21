/**
 * Portal 대시보드 Role 가드 + 리다이렉트 로직 단위 테스트
 *
 * U-05: B2B Role이 없는 사용자는 hasAnyB2BRole()이 false를 반환한다
 * U-06: 미인증 사용자는 getSessionInfo()가 null을 반환한다
 * S-01: 미인증 진입 시 /login으로 리다이렉트 — layout 레벨에서 처리 검증
 * S-02: B2B Role 보유자 진입 시 hasAnyB2BRole()이 true를 반환한다
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockCookiesGet = vi.fn();

vi.mock("next/headers", () => ({
  cookies: () => ({ get: mockCookiesGet }),
}));

function makeJwt(payload: Record<string, unknown>): string {
  const header = Buffer.from(JSON.stringify({ alg: "HS256", typ: "JWT" })).toString("base64url");
  const body = Buffer.from(JSON.stringify(payload)).toString("base64url");
  return `${header}.${body}.sig`;
}

describe("Portal Role 가드 로직", () => {
  beforeEach(() => {
    mockCookiesGet.mockReset();
    vi.resetModules();
  });

  it("[U-06] 미인증 사용자는 getSessionInfo()가 null을 반환한다", async () => {
    mockCookiesGet.mockReturnValue(undefined);
    const { getSessionInfo } = await import("@/lib/server/auth");
    expect(getSessionInfo()).toBeNull();
  });

  it("[U-05] B2B Role 없는 USER는 hasAnyB2BRole()이 false를 반환한다", async () => {
    const token = makeJwt({ sub: 10, email: "user@example.com", roles: ["USER"] });
    mockCookiesGet.mockReturnValue({ value: token });
    const { hasAnyB2BRole } = await import("@/lib/server/auth");
    expect(hasAnyB2BRole()).toBe(false);
  });

  it("[S-01] 미인증 → getSessionInfo() null (layout에서 /login redirect 트리거)", async () => {
    mockCookiesGet.mockReturnValue(undefined);
    const { getSessionInfo } = await import("@/lib/server/auth");
    const session = getSessionInfo();
    // layout은 session === null일 때 redirect("/login")을 호출한다
    expect(session).toBeNull();
  });

  it("[S-02] FACILITY_OWNER 보유자 진입 시 hasAnyB2BRole()이 true를 반환한다", async () => {
    const token = makeJwt({
      sub: 99,
      email: "owner@example.com",
      roles: ["USER", "FACILITY_OWNER"],
    });
    mockCookiesGet.mockReturnValue({ value: token });
    const { hasAnyB2BRole } = await import("@/lib/server/auth");
    expect(hasAnyB2BRole()).toBe(true);
  });

  it("[S-02b] EVENT_HOST 보유자 진입 시 hasAnyB2BRole()이 true를 반환한다", async () => {
    const token = makeJwt({
      sub: 99,
      email: "host@example.com",
      roles: ["EVENT_HOST"],
    });
    mockCookiesGet.mockReturnValue({ value: token });
    const { hasAnyB2BRole } = await import("@/lib/server/auth");
    expect(hasAnyB2BRole()).toBe(true);
  });

  it("[S-02c] GOODS_SELLER 보유자 진입 시 hasAnyB2BRole()이 true를 반환한다", async () => {
    const token = makeJwt({
      sub: 99,
      email: "seller@example.com",
      roles: ["GOODS_SELLER"],
    });
    mockCookiesGet.mockReturnValue({ value: token });
    const { hasAnyB2BRole } = await import("@/lib/server/auth");
    expect(hasAnyB2BRole()).toBe(true);
  });
});

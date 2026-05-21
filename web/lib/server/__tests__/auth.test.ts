/**
 * auth.ts 단위 테스트
 * U-01: B2B Role 0건 사용자 → getB2BRoles() 빈 배열 반환
 * U-02: B2B Role 보유 사용자 → getB2BRoles() 해당 Role 반환
 * U-03: 미인증(쿠키 없음) → getSessionInfo() null 반환
 * U-04: 잘못된 JWT 형식 → getSessionInfo() null 반환
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

// next/headers 모킹은 setup.ts에 없으므로 여기서 직접 모킹
const mockCookiesGet = vi.fn();

vi.mock("next/headers", () => ({
  cookies: () => ({ get: mockCookiesGet }),
}));

/** JWT payload를 Base64URL로 인코딩한 토큰을 생성한다 */
function makeJwt(payload: Record<string, unknown>): string {
  const header = Buffer.from(JSON.stringify({ alg: "HS256", typ: "JWT" })).toString("base64url");
  const body = Buffer.from(JSON.stringify(payload)).toString("base64url");
  return `${header}.${body}.sig`;
}

describe("getSessionInfo", () => {
  beforeEach(() => {
    mockCookiesGet.mockReset();
    vi.resetModules();
  });

  it("[U-03] 쿠키가 없으면 null을 반환한다", async () => {
    mockCookiesGet.mockReturnValue(undefined);
    const { getSessionInfo } = await import("@/lib/server/auth");
    expect(getSessionInfo()).toBeNull();
  });

  it("[U-04] JWT 파트가 3개 미만이면 null을 반환한다", async () => {
    mockCookiesGet.mockReturnValue({ value: "invalid.token" });
    const { getSessionInfo } = await import("@/lib/server/auth");
    expect(getSessionInfo()).toBeNull();
  });

  it("[U-04b] payload JSON 파싱 실패 시 null을 반환한다", async () => {
    mockCookiesGet.mockReturnValue({ value: "header.!!!notbase64!!.sig" });
    const { getSessionInfo } = await import("@/lib/server/auth");
    expect(getSessionInfo()).toBeNull();
  });

  it("유효한 JWT로 userId/email/roles를 반환한다", async () => {
    const token = makeJwt({ sub: 42, email: "user@example.com", roles: ["USER"] });
    mockCookiesGet.mockReturnValue({ value: token });
    const { getSessionInfo } = await import("@/lib/server/auth");
    const session = getSessionInfo();
    expect(session).not.toBeNull();
    expect(session?.userId).toBe(42);
    expect(session?.email).toBe("user@example.com");
    expect(session?.roles).toEqual(["USER"]);
  });
});

describe("getB2BRoles", () => {
  beforeEach(() => {
    mockCookiesGet.mockReset();
    vi.resetModules();
  });

  it("[U-01] roles에 B2B Role이 없으면 빈 배열을 반환한다", async () => {
    const token = makeJwt({ sub: 1, email: "user@example.com", roles: ["USER"] });
    mockCookiesGet.mockReturnValue({ value: token });
    const { getB2BRoles } = await import("@/lib/server/auth");
    expect(getB2BRoles()).toEqual([]);
  });

  it("[U-01b] 미인증이면 빈 배열을 반환한다", async () => {
    mockCookiesGet.mockReturnValue(undefined);
    const { getB2BRoles } = await import("@/lib/server/auth");
    expect(getB2BRoles()).toEqual([]);
  });

  it("[U-02] FACILITY_OWNER를 보유하면 해당 Role이 반환된다", async () => {
    const token = makeJwt({
      sub: 1,
      email: "owner@example.com",
      roles: ["USER", "FACILITY_OWNER"],
    });
    mockCookiesGet.mockReturnValue({ value: token });
    const { getB2BRoles } = await import("@/lib/server/auth");
    const roles = getB2BRoles();
    expect(roles).toContain("FACILITY_OWNER");
    expect(roles).not.toContain("USER");
  });

  it("[U-02b] 다중 B2B Role 보유 시 모두 반환된다", async () => {
    const token = makeJwt({
      sub: 1,
      email: "multi@example.com",
      roles: ["FACILITY_OWNER", "EVENT_HOST"],
    });
    mockCookiesGet.mockReturnValue({ value: token });
    const { getB2BRoles } = await import("@/lib/server/auth");
    const roles = getB2BRoles();
    expect(roles).toContain("FACILITY_OWNER");
    expect(roles).toContain("EVENT_HOST");
  });
});

describe("hasAnyB2BRole", () => {
  beforeEach(() => {
    mockCookiesGet.mockReset();
    vi.resetModules();
  });

  it("[U-01c] B2B Role이 없으면 false를 반환한다", async () => {
    const token = makeJwt({ sub: 1, email: "user@example.com", roles: ["USER"] });
    mockCookiesGet.mockReturnValue({ value: token });
    const { hasAnyB2BRole } = await import("@/lib/server/auth");
    expect(hasAnyB2BRole()).toBe(false);
  });

  it("[U-02c] B2B Role이 있으면 true를 반환한다", async () => {
    const token = makeJwt({
      sub: 1,
      email: "owner@example.com",
      roles: ["GOODS_SELLER"],
    });
    mockCookiesGet.mockReturnValue({ value: token });
    const { hasAnyB2BRole } = await import("@/lib/server/auth");
    expect(hasAnyB2BRole()).toBe(true);
  });
});

/**
 * [S-01] ADMIN 권한 검증 — 비ADMIN 요청 시 403 반환
 * [S-02] ADMIN 권한 — BE에 회원 목록 요청을 forward한다
 * [S-03] BE 401 응답 시 401 + 사용자 친화 메시지를 반환한다
 * [S-04] BE 5xx 응답 시 500 + 사용자 친화 메시지를 반환한다
 * [S-05] BACKEND_URL 미설정 시 503을 반환한다
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

// JWT 페이로드: { sub: 1, email: "admin@example.com", roles: ["ADMIN"] }
const ADMIN_TOKEN =
  "eyJhbGciOiJIUzI1NiJ9." +
  Buffer.from(JSON.stringify({ sub: 1, email: "admin@example.com", roles: ["ADMIN"] })).toString("base64url") +
  ".signature";

// JWT 페이로드: { sub: 2, email: "owner@example.com", roles: ["FACILITY_OWNER"] }
const NON_ADMIN_TOKEN =
  "eyJhbGciOiJIUzI1NiJ9." +
  Buffer.from(JSON.stringify({ sub: 2, email: "owner@example.com", roles: ["FACILITY_OWNER"] })).toString("base64url") +
  ".signature";

const ADMIN_USER_PAGE_RESPONSE = {
  content: [
    {
      userId: 1,
      email: "admin@example.com",
      status: "ACTIVE",
      roleNames: ["ADMIN"],
      joinedAt: "2026-01-01T00:00:00Z",
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

describe("Portal Admin Users Route Handler", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  describe("[S-01] 비ADMIN 요청 시 403 반환", () => {
    it("FACILITY_OWNER 역할만 있는 사용자는 403을 받는다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: NON_ADMIN_TOKEN }),
      } as unknown as ReturnType<typeof cookies>);

      const { GET } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/admin/users");
      const response = await GET(request);

      expect(response.status).toBe(403);
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("해당 작업을 수행할 권한이 없습니다.");
    });

    it("미인증 사용자(쿠키 없음)는 403을 받는다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue(undefined),
      } as unknown as ReturnType<typeof cookies>);

      const { GET } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/admin/users");
      const response = await GET(request);

      expect(response.status).toBe(403);
    });
  });

  describe("[S-02] ADMIN 권한 — BE에 회원 목록 요청을 forward한다", () => {
    it("ADMIN 토큰으로 요청 시 BE 응답을 그대로 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: ADMIN_TOKEN }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify(ADMIN_USER_PAGE_RESPONSE), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/admin/users");
      const response = await GET(request);

      expect(response.status).toBe(200);
      const body = (await response.json()) as typeof ADMIN_USER_PAGE_RESPONSE;
      expect(body.totalElements).toBe(1);
      expect(body.content[0]?.email).toBe("admin@example.com");
    });

    it("page 파라미터를 BE로 forward한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: ADMIN_TOKEN }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ ...ADMIN_USER_PAGE_RESPONSE, page: 1 }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/admin/users?page=1&size=20");
      await GET(request);

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining("/admin/users?page=1&size=20"),
        expect.anything()
      );
    });
  });

  describe("[S-03] BE 401 응답 시 처리", () => {
    it("BE가 401을 반환하면 401 + 사용자 친화 메시지를 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: ADMIN_TOKEN }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ title: "Unauthorized" }), {
          status: 401,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/admin/users");
      const response = await GET(request);

      expect(response.status).toBe(401);
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("로그인이 필요합니다.");
    });
  });

  describe("[S-04] BE 5xx 응답 시 처리", () => {
    it("BE가 500을 반환하면 500 + 사용자 친화 메시지를 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: ADMIN_TOKEN }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ title: "Internal Server Error" }), {
          status: 500,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/admin/users");
      const response = await GET(request);

      expect(response.status).toBe(500);
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
    });
  });

  describe("[S-05] BACKEND_URL 미설정 시 처리", () => {
    it("BACKEND_URL이 없으면 503을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: ADMIN_TOKEN }),
      } as unknown as ReturnType<typeof cookies>);

      delete process.env["BACKEND_URL"];

      const { GET } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/admin/users");
      const response = await GET(request);

      expect(response.status).toBe(503);
    });
  });
});

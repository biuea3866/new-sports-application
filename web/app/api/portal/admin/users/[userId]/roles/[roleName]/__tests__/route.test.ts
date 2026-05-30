/**
 * [S-01] 비ADMIN 요청 시 POST/DELETE 모두 403 반환
 * [S-02] ADMIN — POST 역할 부여를 BE로 forward한다
 * [S-03] ADMIN — DELETE 역할 회수를 BE로 forward한다
 * [S-04] BE 404 응답 시 404 + 사용자 친화 메시지 반환
 * [S-05] BACKEND_URL 미설정 시 503 반환
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

const ADMIN_TOKEN =
  "eyJhbGciOiJIUzI1NiJ9." +
  Buffer.from(JSON.stringify({ sub: 1, email: "admin@example.com", roles: ["ADMIN", "FACILITY_OWNER"] })).toString("base64url") +
  ".signature";

const NON_ADMIN_TOKEN =
  "eyJhbGciOiJIUzI1NiJ9." +
  Buffer.from(JSON.stringify({ sub: 2, email: "owner@example.com", roles: ["FACILITY_OWNER"] })).toString("base64url") +
  ".signature";

const ROUTE_CONTEXT = { params: { userId: "42", roleName: "FACILITY_OWNER" } };

describe("Portal Admin User Role Route Handler", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  describe("[S-01] 비ADMIN 요청 시 403 반환", () => {
    it("POST 요청 시 비ADMIN은 403을 받는다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: NON_ADMIN_TOKEN }),
      } as unknown as ReturnType<typeof cookies>);

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/admin/users/42/roles/FACILITY_OWNER", {
        method: "POST",
      });
      const response = await POST(request, ROUTE_CONTEXT);

      expect(response.status).toBe(403);
    });

    it("DELETE 요청 시 비ADMIN은 403을 받는다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: NON_ADMIN_TOKEN }),
      } as unknown as ReturnType<typeof cookies>);

      const { DELETE } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/admin/users/42/roles/FACILITY_OWNER", {
        method: "DELETE",
      });
      const response = await DELETE(request, ROUTE_CONTEXT);

      expect(response.status).toBe(403);
    });
  });

  describe("[S-02] ADMIN — POST 역할 부여를 BE로 forward한다", () => {
    it("POST 성공 시 200을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: ADMIN_TOKEN }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(new Response(null, { status: 200 }));

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/admin/users/42/roles/FACILITY_OWNER", {
        method: "POST",
      });
      const response = await POST(request, ROUTE_CONTEXT);

      expect(response.status).toBe(200);
      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining("/admin/users/42/roles/FACILITY_OWNER"),
        expect.objectContaining({ method: "POST" })
      );
    });
  });

  describe("[S-03] ADMIN — DELETE 역할 회수를 BE로 forward한다", () => {
    it("DELETE 성공 시 200을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: ADMIN_TOKEN }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(new Response(null, { status: 200 }));

      const { DELETE } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/admin/users/42/roles/FACILITY_OWNER", {
        method: "DELETE",
      });
      const response = await DELETE(request, ROUTE_CONTEXT);

      expect(response.status).toBe(200);
      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining("/admin/users/42/roles/FACILITY_OWNER"),
        expect.objectContaining({ method: "DELETE" })
      );
    });
  });

  describe("[S-04] BE 404 응답 시 처리", () => {
    it("BE가 404를 반환하면 404 + 사용자 친화 메시지를 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: ADMIN_TOKEN }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ title: "Not Found" }), {
          status: 404,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/admin/users/42/roles/FACILITY_OWNER", {
        method: "POST",
      });
      const response = await POST(request, ROUTE_CONTEXT);

      expect(response.status).toBe(404);
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("요청한 리소스를 찾을 수 없습니다.");
    });
  });

  describe("[S-05] BACKEND_URL 미설정 시 처리", () => {
    it("BACKEND_URL이 없으면 503을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: ADMIN_TOKEN }),
      } as unknown as ReturnType<typeof cookies>);

      delete process.env["BACKEND_URL"];

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/admin/users/42/roles/FACILITY_OWNER", {
        method: "POST",
      });
      const response = await POST(request, ROUTE_CONTEXT);

      expect(response.status).toBe(503);
    });
  });
});

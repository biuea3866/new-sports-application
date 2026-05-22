/**
 * BFF Route Handler 테스트 — Admin MCP Audit Logs
 * GET 쿼리 파라미터 forward, BE 401/5xx 동작
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("Admin MCP Audit Logs Route Handler", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  describe("[U-01] GET 쿼리 forward", () => {
    it("[happy path] 유효한 쿼리 파라미터를 BE에 forward하고 200을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const beBody = {
        content: [
          {
            id: 1,
            tokenId: 10,
            toolName: "search_players",
            paramsMasked: null,
            statusCode: 200,
            latencyMs: 123,
            ipAddr: "127.0.0.1",
            calledAt: "2026-05-20T10:00:00Z",
          },
        ],
        totalElements: 1,
        totalPages: 1,
        pageNumber: 0,
        pageSize: 20,
      };
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify(beBody), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/admin/mcp/audit-logs?from=2026-05-14T00:00:00Z&to=2026-05-20T23:59:59Z&page=0&size=20"
      );
      const response = await GET(request);

      expect(response.status).toBe(200);
      expect(mockFetch).toHaveBeenCalledTimes(1);
      const url = (mockFetch.mock.calls[0] as unknown[])[0] as string;
      expect(url).toContain("/api/admin/mcp/audit-logs");
      expect(url).toContain("from=");
      expect(url).toContain("to=");
    });

    it("쿼리 파라미터 없이 호출해도 BE로 forward한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const beBody = {
        content: [],
        totalElements: 0,
        totalPages: 0,
        pageNumber: 0,
        pageSize: 20,
      };
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify(beBody), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/admin/mcp/audit-logs"
      );
      const response = await GET(request);

      expect(response.status).toBe(200);
      expect(mockFetch).toHaveBeenCalledTimes(1);
    });
  });

  describe("[S-01] BE 401 응답을 401 + WWW-Authenticate 헤더로 forward", () => {
    it("BE가 401을 반환하면 Route Handler도 401 + WWW-Authenticate 헤더를 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue(undefined),
      } as unknown as ReturnType<typeof cookies>);

      const beHeaders = new Headers({
        "Content-Type": "application/json",
        "WWW-Authenticate": 'Bearer realm="api"',
      });
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ title: "Unauthorized", status: 401 }), {
          status: 401,
          headers: beHeaders,
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/admin/mcp/audit-logs?from=2026-05-14T00:00:00Z&to=2026-05-20T23:59:59Z"
      );
      const response = await GET(request);

      expect(response.status).toBe(401);
      expect(response.headers.get("WWW-Authenticate")).toBe('Bearer realm="api"');
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("로그인이 필요합니다.");
    });
  });

  describe("[S-02] BE 5xx 응답 시 사용자 친화 메시지 반환", () => {
    it("BE가 500을 반환하면 500 + 사용자 친화 메시지를 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ title: "Internal Server Error" }), {
          status: 500,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/admin/mcp/audit-logs?from=2026-05-14T00:00:00Z&to=2026-05-20T23:59:59Z"
      );
      const response = await GET(request);

      expect(response.status).toBe(500);
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe(
        "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
      );
    });
  });

  describe("[S-03] BACKEND_URL 미설정 시 503 반환", () => {
    it("BACKEND_URL이 없으면 503을 반환한다", async () => {
      delete process.env["BACKEND_URL"];

      const { GET } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/admin/mcp/audit-logs?from=2026-05-14T00:00:00Z&to=2026-05-20T23:59:59Z"
      );
      const response = await GET(request);

      expect(response.status).toBe(503);
    });
  });
});
